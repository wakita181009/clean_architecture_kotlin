package com.wakita181009.cleanarchitecture.infrastructure.repository.jira

import arrow.core.Either
import arrow.core.right
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraIssueRepository
import com.wakita181009.cleanarchitecture.domain.valueobject.Page
import com.wakita181009.cleanarchitecture.domain.valueobject.PageNumber
import com.wakita181009.cleanarchitecture.domain.valueobject.PageSize
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueKey
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssuePriority
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueType
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectId
import com.wakita181009.cleanarchitecture.infrastructure.postgres_gen.enums.JiraIssuePriorityEnum
import com.wakita181009.cleanarchitecture.infrastructure.postgres_gen.enums.JiraIssueTypeEnum
import com.wakita181009.cleanarchitecture.infrastructure.postgres_gen.tables.records.JiraIssueRecord
import com.wakita181009.cleanarchitecture.infrastructure.postgres_gen.tables.references.JIRA_ISSUE
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@Repository
class JiraIssueRepositoryImpl(
    private val dsl: DSLContext,
) : JiraIssueRepository {
    companion object {
        private fun JiraIssueType.toDbEnum(): JiraIssueTypeEnum =
            when (this) {
                JiraIssueType.EPIC -> JiraIssueTypeEnum.epic
                JiraIssueType.STORY -> JiraIssueTypeEnum.story
                JiraIssueType.TASK -> JiraIssueTypeEnum.task
                JiraIssueType.SUBTASK -> JiraIssueTypeEnum.subtask
                JiraIssueType.BUG -> JiraIssueTypeEnum.bug
            }

        private fun JiraIssueTypeEnum.toDomain(): JiraIssueType =
            when (this) {
                JiraIssueTypeEnum.epic -> JiraIssueType.EPIC
                JiraIssueTypeEnum.story -> JiraIssueType.STORY
                JiraIssueTypeEnum.task -> JiraIssueType.TASK
                JiraIssueTypeEnum.subtask -> JiraIssueType.SUBTASK
                JiraIssueTypeEnum.bug -> JiraIssueType.BUG
            }

        private fun JiraIssuePriority.toDbEnum(): JiraIssuePriorityEnum =
            when (this) {
                JiraIssuePriority.HIGHEST -> JiraIssuePriorityEnum.highest
                JiraIssuePriority.HIGH -> JiraIssuePriorityEnum.high
                JiraIssuePriority.MEDIUM -> JiraIssuePriorityEnum.medium
                JiraIssuePriority.LOW -> JiraIssuePriorityEnum.low
                JiraIssuePriority.LOWEST -> JiraIssuePriorityEnum.lowest
            }

        private fun JiraIssuePriorityEnum.toDomain(): JiraIssuePriority =
            when (this) {
                JiraIssuePriorityEnum.highest -> JiraIssuePriority.HIGHEST
                JiraIssuePriorityEnum.high -> JiraIssuePriority.HIGH
                JiraIssuePriorityEnum.medium -> JiraIssuePriority.MEDIUM
                JiraIssuePriorityEnum.low -> JiraIssuePriority.LOW
                JiraIssuePriorityEnum.lowest -> JiraIssuePriority.LOWEST
            }

        private fun JiraIssue.toRecord(): JiraIssueRecord =
            JiraIssueRecord(
                id = id.value,
                projectId = projectId.value,
                key = key.value,
                summary = summary,
                description = description?.let { JSONB.jsonb(it) },
                issueType = issueType.toDbEnum(),
                priority = priority.toDbEnum(),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

        private fun JiraIssueRecord.toDomain(): JiraIssue =
            JiraIssue(
                id = JiraIssueId(id!!),
                projectId = JiraProjectId(projectId!!),
                key = JiraIssueKey(key!!),
                summary = summary!!,
                description = description.toString(),
                issueType = issueType!!.toDomain(),
                priority = priority!!.toDomain(),
                createdAt = createdAt!!,
                updatedAt = updatedAt!!,
            )
    }

    override suspend fun findByIds(ids: List<JiraIssueId>): Either<JiraError, List<JiraIssue>> {
        if (ids.isEmpty()) return emptyList<JiraIssue>().right()

        return Either
            .catch {
                Flux
                    .from(
                        dsl
                            .selectFrom(JIRA_ISSUE)
                            .where(JIRA_ISSUE.ID.`in`(ids.map { it.value })),
                    ).map { it.toDomain() }
                    .collectList()
                    .awaitSingle()
            }.mapLeft { e ->
                JiraError.DatabaseError(
                    message = "Failed to fetch JIRA issues: ${e.message}",
                    cause = e,
                )
            }
    }

    override suspend fun list(
        pageNumber: PageNumber,
        pageSize: PageSize,
    ): Either<JiraError, Page<JiraIssue>> {
        val offset = (pageNumber.value - 1) * pageSize.value
        val countMono =
            dsl
                .selectCount()
                .from(JIRA_ISSUE)
                .toMono()
                .map { it.value1() }
        val itemsMono =
            Flux
                .from(
                    dsl
                        .selectFrom(JIRA_ISSUE)
                        .orderBy(JIRA_ISSUE.CREATED_AT.desc())
                        .limit(pageSize.value)
                        .offset(offset),
                ).map { it.toDomain() }
                .collectList()

        return Either
            .catch {
                Mono.zip(countMono, itemsMono).map { Page(it.t1, it.t2) }.awaitSingle()
            }.mapLeft { e ->
                JiraError.DatabaseError(
                    message = "Failed to list JIRA issues: ${e.message}",
                    cause = e,
                )
            }
    }

    override suspend fun bulkUpsert(issues: List<JiraIssue>): Either<JiraError, List<JiraIssue>> {
        if (issues.isEmpty()) return emptyList<JiraIssue>().right()

        return Either
            .catch {
                val records = issues.map { it.toRecord() }
                val queries =
                    records.map { record ->
                        dsl
                            .insertInto(JIRA_ISSUE)
                            .set(record)
                            .onConflict(JIRA_ISSUE.ID)
                            .doUpdate()
                            .set(JIRA_ISSUE.PROJECT_ID, record.projectId)
                            .set(JIRA_ISSUE.KEY, record.key)
                            .set(JIRA_ISSUE.SUMMARY, record.summary)
                            .set(JIRA_ISSUE.DESCRIPTION, record.description)
                            .set(JIRA_ISSUE.ISSUE_TYPE, record.issueType)
                            .set(JIRA_ISSUE.PRIORITY, record.priority)
                            .set(JIRA_ISSUE.UPDATED_AT, record.updatedAt)
                    }
                dsl
                    .batch(queries)
                    .toMono()
                    .awaitSingleOrNull()
                issues
            }.mapLeft { e ->
                JiraError.DatabaseError(
                    message = "Failed to bulk upsert JIRA issues: ${e.message}",
                    cause = e,
                )
            }
    }
}
