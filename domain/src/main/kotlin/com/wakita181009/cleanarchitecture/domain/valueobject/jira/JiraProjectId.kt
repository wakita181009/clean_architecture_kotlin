package com.wakita181009.cleanarchitecture.domain.valueobject.jira

@JvmInline
value class JiraProjectId private constructor(
    val value: Long,
) {
    companion object {
        operator fun invoke(value: Long) = JiraProjectId(value)
    }
}
