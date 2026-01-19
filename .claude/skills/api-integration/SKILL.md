---
name: api-integration
description: External API integration patterns. Port/Adapter implementation, DTOs, pagination, retry with exponential backoff, and error handling.
---

# External API Integration Patterns

Patterns for integrating with external APIs.

## Overview

External API integrations follow a consistent pattern:

1. **Port Interface** in domain layer - defines the contract
2. **Adapter Implementation** in infrastructure layer - implements the actual API calls
3. **DTOs** for request/response mapping
4. **Resilience** with retry and rate limiting

## Port Interface Pattern

Ports are defined in the domain layer and return `Flow<Either<Error, List<Entity>>>` for streaming data with pagination.

```kotlin
// domain/port/jira/JiraIssuePort.kt
interface JiraIssuePort {
    fun fetchIssues(
        projectKeys: List<JiraProjectKey>,
        since: OffsetDateTime,
    ): Flow<Either<JiraError, List<JiraIssue>>>
}
```

### Key Principles

- Return `Flow` for paginated endpoints to enable streaming
- Use domain-specific error types (not exceptions)
- Accept domain value objects as parameters
- Return domain entities (not DTOs)

## Adapter Implementation Pattern

```kotlin
@Component
class JiraIssueAdapterImpl(
    private val okHttpClient: OkHttpClient,
    @param:Qualifier("jiraApiToken") private val jiraApiToken: String,
) : JiraIssuePort {

    companion object {
        private const val BASE_URL = "https://your-domain.atlassian.net/rest/api/3"
        private const val MAX_RESULTS = 100
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_INTERVAL = 500L
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val API_CALL_DELAY_MS = 1000L

        private val jsonMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        private val retryConfig = RetryConfig.custom<Any>()
            .maxAttempts(MAX_ATTEMPTS)
            .intervalFunction(
                IntervalFunction.ofExponentialBackoff(INITIAL_BACKOFF_INTERVAL, BACKOFF_MULTIPLIER)
            ).build()
    }

    override fun fetchIssues(
        projectKeys: List<JiraProjectKey>,
        since: OffsetDateTime,
    ): Flow<Either<JiraError, List<JiraIssue>>> =
        flow {
            var nextPageToken: String? = null
            do {
                val result = fetchPage(projectKeys, nextPageToken)
                result
                    .onLeft { emit(it.left()) }
                    .onRight { response ->
                        emit(response.issues.mapNotNull { it.toDomain() }.right())
                        nextPageToken = response.nextPageToken
                    }
                if (result.isLeft() || result.getOrNull()?.isLast == true) break
                delay(API_CALL_DELAY_MS)
            } while (true)
        }.flowOn(Dispatchers.IO)
}
```

## DTO Pattern

DTOs are defined alongside the adapter and include `toDomain()` conversion methods.

```kotlin
// infrastructure/adapter/jira/JiraApiDto.kt
data class JiraSearchResponse(
    val issues: List<JiraIssueResponse>,
    val isLast: Boolean,
    val nextPageToken: String?,
)

data class JiraIssueResponse(
    val id: Long,
    val key: String,
    val fields: JiraIssueFields,
) {
    // Nullable return type - gracefully handles unknown enum values
    fun toDomain(): JiraIssue? {
        val issueType = JiraIssueType.entries.find { it.name == fields.issuetype.name }
            ?: return null  // Skip unknown issue types
        val priority = JiraIssuePriority.entries.find { it.name == fields.priority.name }
            ?: return null  // Skip unknown priorities
        return JiraIssue(
            id = JiraIssueId(id),
            projectId = JiraProjectId(fields.project.id),
            key = JiraIssueKey(key),
            // ...
        )
    }
}
```

## Pagination Patterns

### Cursor-Based Pagination

```kotlin
var nextPageToken: String? = null
do {
    val result = fetchPage(jql, nextPageToken)
    val isLast = result
        .onLeft { emit(it.left()) }
        .onRight { response ->
            emit(response.issues.mapNotNull { it.toDomain() }.right())
            nextPageToken = response.nextPageToken
        }.fold({ true }, { it.isLast })
    if (isLast) break
    delay(API_CALL_DELAY_MS)
} while (true)
```

## Resilience Configuration

### Retry with Exponential Backoff

```kotlin
private val retryConfig = RetryConfig.custom<Any>()
    .maxAttempts(3)
    .intervalFunction(
        IntervalFunction.ofExponentialBackoff(500L, 2.0)
    ).build()

// Usage
Retry.of("fetchData", retryConfig)
    .decorateSuspendFunction {
        // API call
    }.invoke()
```

### Backoff Timing

| Attempt | Wait Time |
|---------|-----------|
| 1 | 500ms |
| 2 | 1000ms |
| 3 | 2000ms |

## Error Handling Pattern

```kotlin
private suspend fun fetchPage(
    projectKeys: List<JiraProjectKey>,
    nextPageToken: String?,
): Either<JiraError, JiraSearchResponse> =
    Either
        .catch {
            Retry
                .of("fetchIssues", retryConfig)
                .decorateSuspendFunction {
                    val request = Request.Builder()
                        .url("$BASE_URL/search/jql")
                        .addHeader("Authorization", "Basic $jiraApiToken")
                        .post(requestBody)
                        .build()

                    okHttpClient.newCall(request).execute().use { res ->
                        jsonMapper.readValue<JiraSearchResponse>(res.body.string())
                    }
                }.invoke()
        }.mapLeft { e ->
            JiraError.ApiError(
                message = "Failed to fetch issues from Jira API: ${e.message}",
                cause = e,
            )
        }
```

## Selective Sync Pattern

Reduce API calls by only processing records within a specific time window:

```kotlin
class JiraIssueSyncUseCaseImpl(...) : JiraIssueSyncUseCase {
    companion object {
        private const val SYNC_WINDOW_DAYS = 180L
    }

    override suspend fun execute(): Either<JiraIssueSyncError, Int> =
        either {
            val since = OffsetDateTime.now().minusDays(SYNC_WINDOW_DAYS)
            jiraIssuePort.fetchIssues(projectKeys, since).collect { ... }
        }
}
```

| Scenario | Recommended Window | Reason |
|----------|-------------------|--------|
| Active development | 180 days | Captures active work |
| Recent activity | 90 days | Focused analysis |
| Historical data | 365 days | Comprehensive dataset |

## Implementation Checklist

When adding a new API integration:

- [ ] Define port interface in `domain/port/{api}/`
- [ ] Define error types in `domain/error/{Api}Error.kt`
- [ ] Create adapter implementation in `infrastructure/adapter/{api}/`
- [ ] Define DTOs with `@JsonProperty` annotations
- [ ] Implement `toDomain()` methods on DTOs
- [ ] Configure retry with exponential backoff
- [ ] Add rate limiting delay between API calls
- [ ] Handle pagination (cursor or offset based)
- [ ] Register API token bean in `AdapterConfig`
