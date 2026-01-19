---
name: backend-patterns
description: Backend architecture patterns, API design, database optimization, and server-side best practices for Kotlin, Spring Boot WebFlux, and Clean Architecture.
---

# Backend Development Patterns

Backend architecture patterns and best practices for Kotlin/Spring Boot WebFlux applications.

> **Note**: For Clean Architecture patterns, see `skills/clean-architecture/SKILL.md`.
> For GraphQL patterns, see `skills/graphql-presentation/SKILL.md`.
> For Job Runner patterns, see `skills/job-runner/SKILL.md`.

## Database Patterns (jOOQ + R2DBC)

### Query Optimization

```kotlin
// GOOD: Select only needed columns
suspend fun findSummaries(): Either<JiraError, List<IssueSummary>> {
    return Either.catch {
        dsl.select(JIRA_ISSUES.ID, JIRA_ISSUES.KEY, JIRA_ISSUES.SUMMARY)
            .from(JIRA_ISSUES)
            .where(JIRA_ISSUES.STATUS.ne("DONE"))
            .orderBy(JIRA_ISSUES.UPDATED_AT.desc())
            .limit(100)
            .awaitAll()
            .map { record ->
                IssueSummary(
                    id = JiraIssueId(record[JIRA_ISSUES.ID]),
                    key = JiraIssueKey(record[JIRA_ISSUES.KEY]),
                    summary = record[JIRA_ISSUES.SUMMARY],
                )
            }
    }.mapLeft { JiraError.DatabaseError(it.message ?: "Unknown error") }
}

// BAD: Select everything
suspend fun findSummaries() {
    dsl.selectFrom(JIRA_ISSUES)  // Fetches all columns unnecessarily
        .awaitAll()
}
```

### N+1 Query Prevention

```kotlin
// BAD: N+1 query problem
suspend fun getIssuesWithProjects(): List<IssueWithProject> {
    val issues = repository.findAll()
    return issues.map { issue ->
        val project = projectRepository.findById(issue.projectId)  // N queries!
        IssueWithProject(issue, project)
    }
}

// GOOD: Batch fetch with DataLoader
@Component
class JiraProjectDataLoader(
    private val repository: JiraProjectRepository,
) : KotlinDataLoader<String, JiraProjectType?> {

    override val dataLoaderName = "JiraProjectDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, JiraProjectType?> =
        DataLoaderFactory.newDataLoader { ids ->
            CoroutineScope(Dispatchers.IO).future {
                repository.findByIds(ids.map { JiraProjectId(it) })
                    .map { projects ->
                        val projectMap = projects.associateBy { it.id.value }
                        ids.map { id -> projectMap[id]?.let { JiraProjectType.from(it) } }
                    }
                    .getOrElse { ids.map { null } }
            }
        }
}
```

### Transaction Pattern

```kotlin
// Port in application layer
interface TransactionExecutor {
    suspend fun <T> executeInTransaction(block: suspend () -> T): T
}

// Implementation in infrastructure layer
@Component
class TransactionExecutorImpl(
    private val transactionalOperator: TransactionalOperator,
) : TransactionExecutor {

    override suspend fun <T> executeInTransaction(block: suspend () -> T): T {
        return transactionalOperator.executeAndAwait { block() }
    }
}

// Usage in UseCase
class JiraIssueSyncUseCaseImpl(
    private val transactionExecutor: TransactionExecutor,
    private val apiClient: JiraIssuePort,
    private val repository: JiraIssueRepository,
) : JiraIssueSyncUseCase {

    override suspend fun execute(): Either<JiraIssueSyncError, SyncResult> {
        return apiClient.fetchAllIssues()
            .mapLeft { JiraIssueSyncError.from(it) }
            .flatMap { dtos ->
                transactionExecutor.executeInTransaction {
                    val issues = dtos.map { it.toDomain() }
                    repository.saveAll(issues)
                        .mapLeft { JiraIssueSyncError.from(it) }
                        .map { SyncResult(saved = it.size) }
                }
            }
    }
}
```

## Error Handling Patterns

### Two-Layer Error Architecture

```kotlin
// Domain errors (domain layer)
sealed class JiraError : DomainError {
    data class NotFound(val id: JiraIssueId) : JiraError()
    data class DatabaseError(val message: String) : JiraError()
    data class ApiError(val code: Int, val message: String) : JiraError()
    data class NetworkError(val message: String) : JiraError()
}

// Application errors (application layer)
sealed class JiraIssueSyncError : ApplicationError {
    data class ApiFailure(val error: JiraError) : JiraIssueSyncError()
    data class DatabaseFailure(val error: JiraError) : JiraIssueSyncError()
    data class TransactionFailure(val message: String) : JiraIssueSyncError()

    companion object {
        fun from(error: JiraError): JiraIssueSyncError = when (error) {
            is JiraError.ApiError, is JiraError.NetworkError -> ApiFailure(error)
            is JiraError.DatabaseError -> DatabaseFailure(error)
            is JiraError.NotFound -> ApiFailure(error)
        }
    }
}
```

### Either.catch Pattern

```kotlin
// Wrap potentially throwing code
suspend fun fetchIssue(key: JiraIssueKey): Either<JiraError, JiraIssue> {
    return Either.catch {
        // Code that might throw
        httpClient.fetch(key)
    }.mapLeft { throwable ->
        // Convert exception to domain error
        when (throwable) {
            is HttpException -> JiraError.ApiError(throwable.code, throwable.message)
            is IOException -> JiraError.NetworkError(throwable.message ?: "Network error")
            else -> JiraError.UnknownError(throwable.message ?: "Unknown error")
        }
    }
}
```

### Retry with Exponential Backoff

```kotlin
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    maxDelay: Long = 30_000L,
    factor: Double = 2.0,
    block: suspend () -> Either<JiraError, T>,
): Either<JiraError, T> {
    var currentDelay = initialDelay
    var lastError: JiraError? = null

    repeat(maxRetries) { attempt ->
        val result = block()

        result.fold(
            ifLeft = { error ->
                lastError = error
                if (attempt < maxRetries - 1 && error.isRetryable()) {
                    delay(currentDelay)
                    currentDelay = minOf((currentDelay * factor).toLong(), maxDelay)
                }
            },
            ifRight = { return result }
        )
    }

    return lastError!!.left()
}

private fun JiraError.isRetryable(): Boolean = when (this) {
    is JiraError.NetworkError -> true
    is JiraError.ApiError -> code in listOf(429, 500, 502, 503, 504)
    else -> false
}
```

## Logging & Monitoring

### Structured Logging

```kotlin
class JiraIssueSyncUseCaseImpl(
    private val apiClient: JiraIssuePort,
    private val repository: JiraIssueRepository,
) : JiraIssueSyncUseCase {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun execute(): Either<JiraIssueSyncError, SyncResult> {
        logger.info("Starting Jira issue sync")
        val startTime = System.currentTimeMillis()

        return apiClient.fetchAllIssues()
            .mapLeft { error ->
                logger.error("Failed to fetch issues from Jira API: $error")
                JiraIssueSyncError.from(error)
            }
            .flatMap { dtos ->
                logger.info("Fetched ${dtos.size} issues from Jira API")

                repository.saveAll(dtos.map { it.toDomain() })
                    .mapLeft { error ->
                        logger.error("Failed to save issues to database: $error")
                        JiraIssueSyncError.from(error)
                    }
                    .map { saved ->
                        val duration = System.currentTimeMillis() - startTime
                        logger.info("Sync completed: saved ${saved.size} issues in ${duration}ms")
                        SyncResult(saved = saved.size)
                    }
            }
    }
}
```

### Health Check Endpoint

```kotlin
@Component
class JiraApiHealthIndicator(
    private val apiClient: JiraIssuePort,
) : ReactiveHealthIndicator {

    override fun health(): Mono<Health> {
        return mono {
            apiClient.healthCheck()
                .fold(
                    ifLeft = { error ->
                        Health.down()
                            .withDetail("error", error.toString())
                            .build()
                    },
                    ifRight = {
                        Health.up()
                            .withDetail("status", "connected")
                            .build()
                    }
                )
        }
    }
}
```

## Configuration Patterns

### Type-Safe Configuration

```kotlin
@ConfigurationProperties(prefix = "jira")
data class JiraProperties(
    val baseUrl: String,
    val apiToken: String,
    val timeout: Duration = Duration.ofSeconds(30),
    val maxRetries: Int = 3,
)

@Configuration
@EnableConfigurationProperties(JiraProperties::class)
class JiraConfig {

    @Bean
    fun jiraOkHttpClient(properties: JiraProperties): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(properties.timeout)
            .readTimeout(properties.timeout)
            .writeTimeout(properties.timeout)
            .addInterceptor(LoggingInterceptor())
            .build()
    }
}
```

### Environment-Specific Configuration

```yaml
# application.yml
jira:
  base-url: ${JIRA_BASE_URL:https://your-domain.atlassian.net}
  api-token: ${JIRA_API_TOKEN}
  timeout: ${JIRA_TIMEOUT:30s}
  max-retries: ${JIRA_MAX_RETRIES:3}

spring:
  r2dbc:
    url: r2dbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DATABASE:cleanarch}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
```

**Remember**: Backend patterns enable scalable, maintainable server-side applications. Choose patterns that fit your complexity level and follow Clean Architecture principles.
