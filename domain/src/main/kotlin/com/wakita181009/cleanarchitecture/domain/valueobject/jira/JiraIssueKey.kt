package com.wakita181009.cleanarchitecture.domain.valueobject.jira

@JvmInline
value class JiraIssueKey private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String) = JiraIssueKey(value)
    }
}
