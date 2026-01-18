package com.wakita181009.cleanarchitecture.domain.error

sealed class JiraError(
    message: String?,
    cause: Throwable? = null,
) : DomainError(message, cause) {
    class InvalidId(
        override val cause: Throwable? = null,
    ) : JiraError("Invalid JIRA issue ID format", cause)

    class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : JiraError(message, cause)

    class ApiError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : JiraError(message, cause)
}
