package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueSyncError
import com.wakita181009.cleanarchitecture.application.port.TransactionExecutor
import com.wakita181009.cleanarchitecture.domain.port.jira.JiraApiClient
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraIssueRepository
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraProjectRepository
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

class JiraIssueSyncUseCaseImpl(
    private val jiraProjectRepository: JiraProjectRepository,
    private val jiraIssueRepository: JiraIssueRepository,
    private val jiraApiClient: JiraApiClient,
    private val transactionExecutor: TransactionExecutor,
) : JiraIssueSyncUseCase {
    private val logger =
        LoggerFactory.getLogger(
            JiraIssueSyncUseCase::class.java,
        )

    companion object {
        private const val ISSUE_CREATED_WITHIN_DAYS = 180L
    }

    override suspend fun execute(): Either<JiraIssueSyncError, Int> =
        either {
            val since =
                OffsetDateTime.now().minusDays(
                    ISSUE_CREATED_WITHIN_DAYS,
                )
            logger.info("Fetching issues created since {}...", since)
            logger.info("Fetching project keys...")
            val projectKeys =
                jiraProjectRepository
                    .findAllProjectKeys()
                    .mapLeft(JiraIssueSyncError::ProjectKeyFetchFailed)
                    .bind()
            logger.info("Found {} project keys: {}", projectKeys.size, projectKeys.map { it.value })

            var totalCount = 0
            jiraApiClient
                .fetchIssues(projectKeys, since)
                .collect { result ->
                    result
                        .onRight { issues ->
                            logger.info("Fetched: {} issues", issues.size)

                            transactionExecutor
                                .executeInTransaction {
                                    jiraIssueRepository.bulkUpsert(issues)
                                }.mapLeft(JiraIssueSyncError::IssuePersistFailed)
                                .bind()

                            totalCount += issues.size
                            logger.info("Saved: {} issues (total: {})", issues.size, totalCount)
                        }.mapLeft(JiraIssueSyncError::IssueFetchFailed)
                        .bind()
                }

            logger.info("Sync completed: {} total issues", totalCount)
            totalCount
        }
}
