package com.wakita181009.cleanarchitecture.framework.runner.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueSyncError
import com.wakita181009.cleanarchitecture.application.usecase.jira.JiraIssueSyncUseCase
import com.wakita181009.cleanarchitecture.framework.runner.SyncJobRunner
import org.springframework.stereotype.Component

@Component
class JiraIssueSyncRunner(
    private val jiraIssueSyncUseCase: JiraIssueSyncUseCase,
) : SyncJobRunner<JiraIssueSyncError>() {
    override val jobName: String = "sync-jira-issue"
    override val entityName: String = "Jira issue"

    override suspend fun execute(): Either<JiraIssueSyncError, Int> = jiraIssueSyncUseCase.execute()
}
