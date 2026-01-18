package com.wakita181009.cleanarchitecture.domain.valueobject.jira

@JvmInline
value class JiraProjectKey private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String) = JiraProjectKey(value)
    }
}
