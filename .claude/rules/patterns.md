# Common Patterns

## API Response Format (GraphQL)

This project uses GraphQL. Responses are typed:

```kotlin
// GraphQL Query
@Controller
class JiraIssueQuery(
    private val jiraIssueListUseCase: JiraIssueListUseCase,
) : Query {
    // Single issue via DataLoader (N+1 prevention)
    fun jiraIssue(
        dfe: DataFetchingEnvironment,
        id: ID,
    ): CompletableFuture<JiraIssue> = dfe.getValueFromDataLoader("JiraIssueDataLoader", id)

    // List with pagination
    suspend fun jiraIssues(
        dfe: DataFetchingEnvironment,
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ) = either {
        jiraIssueListUseCase
            .execute(pageNumber, pageSize)
            .map(JiraIssueList::fromDomain)
            .bind()
    }
}
```

## Repository Pattern

```kotlin
// Port interface (domain layer - no Spring annotations)
interface JiraIssueRepository {
    suspend fun findByIds(ids: List<JiraIssueId>): Either<JiraError, List<JiraIssue>>
    suspend fun list(pageNumber: PageNumber, pageSize: PageSize): Either<JiraError, Page<JiraIssue>>
    suspend fun bulkUpsert(issues: List<JiraIssue>): Either<JiraError, List<JiraIssue>>
}

// Implementation (infrastructure layer)
@Repository
class JiraIssueRepositoryImpl(
    private val dsl: DSLContext,
) : JiraIssueRepository {
    override suspend fun findByIds(ids: List<JiraIssueId>): Either<JiraError, List<JiraIssue>> =
        Either.catch {
            dsl.selectFrom(JIRA_ISSUES)
                .where(JIRA_ISSUES.ID.`in`(ids.map { it.value }))
                .asFlux()
                .collectList()
                .awaitSingle()
                .map { it.toDomain() }
        }.mapLeft { JiraError.DatabaseError(it.message ?: "Unknown error") }
}
```

## UseCase Pattern (Interface + Impl)

```kotlin
// Interface (application layer)
interface JiraIssueFindByIdsUseCase {
    suspend fun execute(ids: List<JiraIssueId>): Either<JiraIssueFindByIdError, List<JiraIssue>>
}

// Implementation (application layer - no Spring annotations)
class JiraIssueFindByIdsUseCaseImpl(
    private val repository: JiraIssueRepository,
) : JiraIssueFindByIdsUseCase {
    override suspend fun execute(ids: List<JiraIssueId>): Either<JiraIssueFindByIdError, List<JiraIssue>> =
        repository.findByIds(ids)
            .mapLeft(JiraIssueFindByIdError::IssueFetchFailed)
}

// DI Configuration (framework layer)
@Configuration
class UseCaseConfig {
    @Bean
    fun jiraIssueFindByIdsUseCase(repository: JiraIssueRepository): JiraIssueFindByIdsUseCase {
        return JiraIssueFindByIdsUseCaseImpl(repository)
    }
}
```

## Value Object Pattern

```kotlin
@JvmInline
value class JiraIssueId private constructor(val value: Long) {
    companion object {
        operator fun invoke(value: Long) = JiraIssueId(value)

        // Factory method with Either for string parsing
        fun of(value: String): Either<JiraError, JiraIssueId> =
            Either.catch {
                JiraIssueId(value.toLong())
            }.mapLeft { e ->
                JiraError.InvalidId(e)
            }
    }
}
```

## Enum Conversion Pattern

NEVER use `valueOf()`, always use explicit `when`:

```kotlin
enum class JiraIssuePriority {
    HIGHEST, HIGH, MEDIUM, LOW, LOWEST;

    companion object {
        fun from(value: String): JiraIssuePriority = when (value.uppercase()) {
            "HIGHEST" -> HIGHEST
            "HIGH" -> HIGH
            "MEDIUM" -> MEDIUM
            "LOW" -> LOW
            "LOWEST" -> LOWEST
            else -> throw IllegalArgumentException("Unknown priority: $value")
        }
    }
}
```

## Transaction Pattern

```kotlin
// Port (application layer)
interface TransactionExecutor {
    suspend fun <T> executeInTransaction(block: suspend () -> Either<*, T>): Either<TransactionError, T>
}

// Implementation (infrastructure layer)
@Component
class TransactionExecutorImpl(
    private val transactionalOperator: TransactionalOperator,
) : TransactionExecutor {
    override suspend fun <T> executeInTransaction(
        block: suspend () -> Either<*, T>
    ): Either<TransactionError, T> =
        Either.catch {
            transactionalOperator.executeAndAwait { block() }
        }.mapLeft { e ->
            TransactionError.ExecutionFailed(e.message ?: "Unknown error", e)
        }.flatMap { result ->
            result.mapLeft { e ->
                TransactionError.ExecutionFailed("Block returned Left: $e", null)
            }
        }
}

// Usage in UseCase
class JiraIssueSyncUseCaseImpl(
    private val transactionExecutor: TransactionExecutor,
    private val repository: JiraIssueRepository,
) : JiraIssueSyncUseCase {
    override suspend fun execute(): Either<JiraIssueSyncError, Int> = either {
        // ... fetch issues ...
        transactionExecutor.executeInTransaction {
            repository.bulkUpsert(issues)
        }.mapLeft(JiraIssueSyncError::IssuePersistFailed).bind()
    }
}
```

## DataLoader Pattern (N+1 Prevention)

```kotlin
@Component
class JiraIssueDataLoader(
    private val jiraIssueFindByIdsUseCase: JiraIssueFindByIdsUseCase,
) : KotlinDataLoader<ID, JiraIssue> {
    override val dataLoaderName = "JiraIssueDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<ID, JiraIssue> =
        DataLoaderFactory.newMappedDataLoader { ids, _ ->
            CoroutineScope(Dispatchers.IO).future {
                loadIssues(ids)
            }
        }

    private suspend fun loadIssues(ids: Set<ID>): Map<ID, Try<JiraIssue>> {
        // Parse IDs with Either for error handling
        val validIds = ids.associateWith { JiraIssueId.of(it.value) }
            .filterValues { it.isRight() }
            .mapValues { (_, v) -> v.getOrNull()!! }

        return jiraIssueFindByIdsUseCase.execute(validIds.values.toList()).fold(
            ifLeft = { error -> ids.associateWith { Try.failed(error) } },
            ifRight = { domainIssues ->
                val resultMap = domainIssues.associateBy { it.id }
                ids.associateWith { id ->
                    validIds[id]?.let { jiraId ->
                        resultMap[jiraId]?.let { Try.succeeded(JiraIssue.fromDomain(it)) }
                            ?: Try.failed(JiraIssueFindByIdError.IssueFetchFailed(JiraError.InvalidId(Exception("Not found"))))
                    } ?: Try.failed(JiraIssueFindByIdError.IssueFetchFailed(JiraError.InvalidId(Exception("Invalid ID"))))
                }
            }
        )
    }
}
```
