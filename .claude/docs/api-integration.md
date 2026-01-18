# External API Integration Patterns

This document defines patterns for integrating with external APIs.

## Overview

External API integrations follow a consistent pattern:

1. **Port Interface** in domain layer - defines the contract
2. **Adapter Implementation** in infrastructure layer - implements the actual API calls
3. **DTOs** for request/response mapping
4. **Resilience** with retry and rate limiting

## Port Interface Pattern

Ports are defined in the domain layer and return `Flow<Either<Error, List<Entity>>>` for streaming data with pagination.

```kotlin
// domain/port/jira/JiraApiClient.kt
interface JiraApiClient {
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

Adapters are implemented in the infrastructure layer with consistent structure.

```kotlin
@Component
class JiraApiClientImpl(
    private val okHttpClient: OkHttpClient,
    @param:Qualifier("jiraApiToken") private val jiraApiToken: String,
) : JiraApiClient {

    companion object {
        // Configuration constants
        private const val BASE_URL = "https://your-domain.atlassian.net/rest/api/3"
        private const val MAX_RESULTS = 100
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_INTERVAL = 500L
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val API_CALL_DELAY_MS = 1000L

        // JSON mapper configuration
        private val jsonMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        // Retry configuration
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

### Companion Object Structure

Always include in companion object:

| Constant | Purpose | Example |
|----------|---------|---------|
| `BASE_URL` | API base URL | `https://example.atlassian.net/rest/api/3` |
| `MAX_RESULTS` | Pagination size | `100` |
| `MAX_ATTEMPTS` | Retry attempts | `3` |
| `INITIAL_BACKOFF_INTERVAL` | Initial backoff (ms) | `500L` |
| `BACKOFF_MULTIPLIER` | Backoff multiplier | `2.0` |
| `API_CALL_DELAY_MS` | Rate limiting delay | `1000L` |
| `jsonMapper` | Jackson ObjectMapper | Configure with JavaTimeModule |
| `retryConfig` | Resilience4j config | Exponential backoff |

## DTO Pattern

DTOs are defined alongside the adapter and include `toDomain()` conversion methods.

```kotlin
// infrastructure/adapter/jira/JiraApiDto.kt
data class JiraSearchRequest(
    val jql: String,
    val fields: List<String>,
    val maxResults: Int,
    val nextPageToken: String?,
)

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
    // Nullable return type - gracefully handles unknown enum values from API
    fun toDomain(): JiraIssue? {
        val issueType = JiraIssueType.entries.find { it.name == fields.issuetype.name }
            ?: return null  // Skip unknown issue types
        val priority = JiraIssuePriority.entries.find { it.name == fields.priority.name }
            ?: return null  // Skip unknown priorities
        return JiraIssue(
            id = JiraIssueId(id),
            projectId = JiraProjectId(fields.project.id),
            key = JiraIssueKey(key),
            summary = fields.summary,
            description = fields.description?.toString(),
            issueType = issueType,
            priority = priority,
            createdAt = fields.created,
            updatedAt = fields.updated,
        )
    }
}
```

### DTO Guidelines

1. **Use `@JsonProperty`** for snake_case to camelCase conversion
2. **Define `toDomain()`** method on each DTO
3. **Handle nullable fields** appropriately
4. **Graceful handling**: Return `null` for unknown enum values

## Pagination Patterns

### Cursor-Based Pagination (Jira)

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

## HTTP Request Building

### Standard Headers (Jira Basic Auth)

```kotlin
Request.Builder()
    .url(url)
    .addHeader("Authorization", "Basic $jiraApiToken")
    .addHeader("Content-Type", "application/json")
    .post(requestBody)
    .build()
```

## Error Handling

### Error Types

Each API has its own sealed class for errors:

```kotlin
// domain/error/JiraError.kt
sealed class JiraError(
    message: String?,
    cause: Throwable? = null,
) : DomainError(message, cause) {
    class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : JiraError(message, cause)

    class ApiError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : JiraError(message, cause)
}
```

### Error Handling Pattern in Adapters

Use `Either.catch { }.mapLeft { ApiError }`:

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
- [ ] Add proper error handling
- [ ] Register API token bean in `AdapterConfig`
- [ ] Add configuration property in `AppProperties`