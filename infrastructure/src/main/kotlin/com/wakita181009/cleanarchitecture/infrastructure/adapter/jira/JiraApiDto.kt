package com.wakita181009.cleanarchitecture.infrastructure.adapter.jira

import arrow.core.Option
import arrow.core.raise.option
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueKey
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssuePriority
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueType
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectId
import java.time.OffsetDateTime

data class JiraSearchRequest(
    val jql: String,
    val fields: List<String>,
    val maxResults: Int,
    val nextPageToken: String?,
)

data class JiraSearchResponse(
    val issues: List<JiraIssueResponse>,
    val isLast: Boolean,
    val nextPageToken: String?,
)

data class JiraIssueResponse(
    val id: Long,
    val key: String,
    val fields: JiraIssueFields,
) {
    fun toDomain(): Option<JiraIssue> =
        option {
            val issueType = fields.issuetype.toDomain().bind()
            val priority = fields.priority.toDomain().bind()
            JiraIssue(
                id =
                    JiraIssueId(id),
                projectId =
                    JiraProjectId(fields.project.id),
                key =
                    JiraIssueKey(key),
                summary = fields.summary,
                description = fields.description?.toString(),
                issueType = issueType,
                priority = priority,
                createdAt = fields.created,
                updatedAt = fields.updated,
            )
        }
}

data class JiraIssueFields(
    val project: JiraProjectResponse,
    val summary: String,
    val description: JsonNode?,
    val issuetype: JiraIssueTypeResponse,
    val priority: JiraPriorityResponse,
    val created: OffsetDateTime,
    val updated: OffsetDateTime,
)

data class JiraProjectResponse(
    val id: Long,
)

data class JiraPriorityResponse(
    val id: String,
    val name: String,
) {
    fun toDomain(): Option<JiraIssuePriority> =
        when (name) {
            "Highest" -> JiraIssuePriority.HIGHEST
            "High" -> JiraIssuePriority.HIGH
            "Medium" -> JiraIssuePriority.MEDIUM
            "Low" -> JiraIssuePriority.LOW
            "Lowest" -> JiraIssuePriority.LOWEST
            else -> null
        }.toOption()
}

data class JiraIssueTypeResponse(
    val id: String,
    val name: String,
) {
    fun toDomain(): Option<JiraIssueType> =
        when (name) {
            "Epic" -> JiraIssueType.EPIC
            "Story" -> JiraIssueType.STORY
            "Task" -> JiraIssueType.TASK
            "Subtask" -> JiraIssueType.SUBTASK
            "Bug" -> JiraIssueType.BUG
            else -> null
        }.toOption()
}
