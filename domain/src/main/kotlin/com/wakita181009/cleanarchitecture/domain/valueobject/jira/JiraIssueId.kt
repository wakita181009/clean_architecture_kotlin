package com.wakita181009.cleanarchitecture.domain.valueobject.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.domain.error.JiraError

@JvmInline
value class JiraIssueId private constructor(
    val value: Long,
) {
    companion object {
        operator fun invoke(value: Long) = JiraIssueId(value)

        fun of(value: String) =
            Either
                .catch {
                    JiraIssueId(value.toLong())
                }.mapLeft { e ->
                    JiraError.InvalidId(e)
                }
    }
}
