package com.wakita181009.cleanarchitecture.application.error.jira

import com.wakita181009.cleanarchitecture.application.error.ApplicationError
import com.wakita181009.cleanarchitecture.domain.error.JiraError

sealed class JiraIssueFindByIdError(
    message: String?,
    cause: Throwable?,
) : ApplicationError(message, cause) {
    class IssueFetchFailed(
        cause: JiraError,
    ) : JiraIssueFindByIdError("Failed to fetch JIRA issues", cause)
}
