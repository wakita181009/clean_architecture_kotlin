package com.wakita181009.cleanarchitecture.framework.config

import com.wakita181009.cleanarchitecture.application.port.TransactionExecutor
import com.wakita181009.cleanarchitecture.application.usecase.jira.JiraIssueFindByIdsUseCaseImpl
import com.wakita181009.cleanarchitecture.application.usecase.jira.JiraIssueSyncUseCaseImpl
import com.wakita181009.cleanarchitecture.domain.port.jira.JiraApiClient
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraIssueRepository
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraProjectRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class UseCaseConfig(
    private val jiraProjectRepository: JiraProjectRepository,
    private val jiraIssueRepository: JiraIssueRepository,
    private val jiraApiClient: JiraApiClient,
    private val transactionExecutor: TransactionExecutor,
) {
    @Bean
    open fun jiraIssueFindByIdsUseCase() =
        JiraIssueFindByIdsUseCaseImpl(
            jiraIssueRepository = jiraIssueRepository,
        )

    @Bean
    open fun jiraIssueSyncUseCase() =
        JiraIssueSyncUseCaseImpl(
            jiraProjectRepository = jiraProjectRepository,
            jiraIssueRepository = jiraIssueRepository,
            jiraApiClient = jiraApiClient,
            transactionExecutor = transactionExecutor,
        )
}
