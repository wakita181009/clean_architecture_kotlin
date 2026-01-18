package com.wakita181009.cleanarchitecture.application.error.jira

import com.wakita181009.cleanarchitecture.application.error.ApplicationError
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.error.PageNumberError
import com.wakita181009.cleanarchitecture.domain.error.PageSizeError

sealed class JiraIssueListError(
    message: String?,
    cause: Throwable?,
) : ApplicationError(message, cause) {
    class InvalidPageNumber(
        error: PageNumberError,
    ) : JiraIssueListError(error.message, null)

    class InvalidPageSize(
        error: PageSizeError,
    ) : JiraIssueListError(error.message, null)

    class IssueFetchFailed(
        cause: JiraError,
    ) : JiraIssueListError("Failed to fetch JIRA issues", cause)
}
