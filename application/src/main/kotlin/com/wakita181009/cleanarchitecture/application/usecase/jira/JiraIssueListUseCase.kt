package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueListError
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.valueobject.Page

interface JiraIssueListUseCase {
    suspend fun execute(
        pageNumber: Int,
        pageSize: Int,
    ): Either<JiraIssueListError, Page<JiraIssue>>
}
