package com.wakita181009.cleanarchitecture.domain.entity.jira

import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueKey
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssuePriority
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueType
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectId
import java.time.OffsetDateTime

data class JiraIssue(
    val id: JiraIssueId,
    val projectId: JiraProjectId,
    val key: JiraIssueKey,
    val summary: String,
    val description: String?,
    val issueType: JiraIssueType,
    val priority: JiraIssuePriority,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
