package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueFindByIdError
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId

interface JiraIssueFindByIdsUseCase {
    suspend fun execute(ids: List<JiraIssueId>): Either<JiraIssueFindByIdError, List<JiraIssue>>
}
