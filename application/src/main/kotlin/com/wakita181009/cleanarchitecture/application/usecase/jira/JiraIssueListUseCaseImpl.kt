package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueListError
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraIssueRepository
import com.wakita181009.cleanarchitecture.domain.valueobject.Page
import com.wakita181009.cleanarchitecture.domain.valueobject.PageNumber
import com.wakita181009.cleanarchitecture.domain.valueobject.PageSize

class JiraIssueListUseCaseImpl(
    private val jiraIssueRepository: JiraIssueRepository,
) : JiraIssueListUseCase {
    override suspend fun execute(
        pageNumber: Int,
        pageSize: Int,
    ): Either<JiraIssueListError, Page<JiraIssue>> =
        either {
            val validPageNumber =
                PageNumber
                    .of(pageNumber)
                    .mapLeft(JiraIssueListError::InvalidPageNumber)
                    .bind()
            val validPageSize =
                PageSize
                    .of(pageSize)
                    .mapLeft(JiraIssueListError::InvalidPageSize)
                    .bind()

            jiraIssueRepository
                .list(
                    pageNumber = validPageNumber,
                    pageSize = validPageSize,
                ).mapLeft(JiraIssueListError::IssueFetchFailed)
                .bind()
        }
}
