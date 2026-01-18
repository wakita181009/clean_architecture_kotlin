package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueFindByIdError
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraIssueRepository
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId

class JiraIssueFindByIdsUseCaseImpl(
    private val jiraIssueRepository: JiraIssueRepository,
) : JiraIssueFindByIdsUseCase {
    override suspend fun execute(ids: List<JiraIssueId>): Either<JiraIssueFindByIdError, List<JiraIssue>> =
        either {
            jiraIssueRepository
                .findByIds(ids)
                .mapLeft(JiraIssueFindByIdError::IssueFetchFailed)
                .bind()
        }
}
