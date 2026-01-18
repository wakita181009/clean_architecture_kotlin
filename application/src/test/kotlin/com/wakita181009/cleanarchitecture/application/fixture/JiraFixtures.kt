package com.wakita181009.cleanarchitecture.application.fixture

import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueKey
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssuePriority
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueType
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectId
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectKey
import java.time.OffsetDateTime

object JiraFixtures {
    fun createProjectKey(key: String = "TEST"): JiraProjectKey =
        _root_ide_package_.com.wakita181009.cleanarchitecture.domain.valueobject.jira
            .JiraProjectKey(key)

    fun createJiraIssue(
        id: Long = 1L,
        projectId: Long = 100L,
        key: String = "TEST-1",
        summary: String = "Test issue summary",
        description: String? = "Test issue description",
        issueType: JiraIssueType = JiraIssueType.TASK,
        priority: JiraIssuePriority = JiraIssuePriority.MEDIUM,
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now(),
    ): JiraIssue =
        _root_ide_package_.com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue(
            id =
                _root_ide_package_.com.wakita181009.cleanarchitecture.domain.valueobject.jira
                    .JiraIssueId(id),
            projectId =
                _root_ide_package_.com.wakita181009.cleanarchitecture.domain.valueobject.jira
                    .JiraProjectId(projectId),
            key =
                _root_ide_package_.com.wakita181009.cleanarchitecture.domain.valueobject.jira
                    .JiraIssueKey(key),
            summary = summary,
            description = description,
            issueType = issueType,
            priority = priority,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun createJiraIssues(
        count: Int,
        projectId: Long = 100L,
        keyPrefix: String = "TEST",
    ): List<JiraIssue> =
        (1..count).map { index ->
            createJiraIssue(
                id = index.toLong(),
                projectId = projectId,
                key = "$keyPrefix-$index",
                summary = "Test issue $index",
            )
        }
}
