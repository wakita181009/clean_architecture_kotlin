package com.wakita181009.cleanarchitecture.application.error.jira

import com.wakita181009.cleanarchitecture.application.error.ApplicationError
import com.wakita181009.cleanarchitecture.application.error.TransactionError
import com.wakita181009.cleanarchitecture.domain.error.JiraError

sealed class JiraIssueSyncError(
    message: String?,
    cause: Throwable?,
) : ApplicationError(message, cause) {
    class ProjectKeyFetchFailed(
        cause: JiraError,
    ) : JiraIssueSyncError("Failed to fetch project keys", cause)

    class IssueFetchFailed(
        cause: JiraError,
    ) : JiraIssueSyncError("Failed to fetch issues", cause)

    class IssuePersistFailed(
        cause: TransactionError,
    ) : JiraIssueSyncError("Failed to persist issues", cause)
}
