# Selective Sync Pattern

This document defines the selective sync pattern for efficiently synchronizing data from external APIs.

## Overview

When syncing large amounts of data from external APIs, synchronizing all records is inefficient. The **Selective Sync Pattern** reduces API calls by only processing records within a specific time window.

## Pattern Implementation

### 1. Time-Based Filtering in API Client

Apply time-based filters in API queries:

```kotlin
// infrastructure/adapter/jira/JiraApiClientImpl.kt
override fun fetchIssues(
    projectKeys: List<JiraProjectKey>,
    since: OffsetDateTime,
): Flow<Either<JiraError, List<JiraIssue>>> =
    flow {
        // JQL filters issues created within the time window
        val jql = "project in (${projectKeys.joinToString(", ") { it.value }}) AND created >= -180d"
        // ... pagination and API calls
    }
```

### 2. UseCase with Time Window

Define the sync window in the UseCase (Interface + Impl pattern, no Spring annotations):

```kotlin
// application/usecase/jira/JiraIssueSyncUseCase.kt (Interface)
interface JiraIssueSyncUseCase {
    suspend fun execute(): Either<JiraIssueSyncError, Int>
}

// application/usecase/jira/JiraIssueSyncUseCaseImpl.kt (Implementation)
class JiraIssueSyncUseCaseImpl(
    private val jiraProjectRepository: JiraProjectRepository,
    private val jiraIssueRepository: JiraIssueRepository,
    private val jiraApiClient: JiraApiClient,
    private val transactionExecutor: TransactionExecutor,
) : JiraIssueSyncUseCase {
    companion object {
        private const val SYNC_WINDOW_DAYS = 180L
    }

    override suspend fun execute(): Either<JiraIssueSyncError, Int> =
        either {
            val projectKeys = jiraProjectRepository
                .findAllProjectKeys()
                .mapLeft(JiraIssueSyncError::ProjectKeyFetchFailed)
                .bind()
            val since = OffsetDateTime.now().minusDays(SYNC_WINDOW_DAYS)

            var totalCount = 0
            jiraApiClient.fetchIssues(projectKeys, since).collect { result ->
                result.onRight { issues ->
                    transactionExecutor
                        .executeInTransaction {
                            jiraIssueRepository.bulkUpsert(issues)
                        }.mapLeft(JiraIssueSyncError::IssuePersistFailed)
                        .bind()
                    totalCount += issues.size
                }.mapLeft(JiraIssueSyncError::IssueFetchFailed)
                    .bind()
            }
            totalCount
        }
}
```

## Time Window Configuration

| Scenario | Recommended Window | Reason |
|----------|-------------------|--------|
| Jira Issues | 180 days | Captures active development |
| Recent activity | 90 days | Shorter for focused analysis |
| Historical data | 365 days | Comprehensive dataset |

The time window should be:
- **Long enough** to capture meaningful data for analysis
- **Short enough** to reduce API calls and processing time

## Benefits

1. **Reduced API Calls**: Only fetch relevant data
2. **Faster Execution**: Skip old/dormant records
3. **Lower Rate Limit Risk**: Fewer API calls means less chance of hitting rate limits
4. **Efficient Resource Usage**: Less memory and database load

## When to Use

Use selective sync when:

- **Large datasets**: Too many records to sync all at once
- **Time-sensitive data**: Only recent data is relevant
- **API has rate limits**: Reducing calls helps stay within limits
- **API supports time-based filtering**: JQL, date parameters, etc.

## Implementation Checklist

When implementing selective sync:

- [ ] Define time window constant in UseCase companion object
- [ ] Apply time filter in API query (JQL, query parameters, etc.)
- [ ] Calculate `since` timestamp from current time
- [ ] Log the sync window for monitoring
- [ ] Document the time window in CLAUDE.md

## Related Patterns

- **Streaming Pattern**: Process paginated data with Flow
- **Batch Upsert Pattern**: Efficiently persist fetched data
- **Error Handling Pattern**: Handle partial failures gracefully