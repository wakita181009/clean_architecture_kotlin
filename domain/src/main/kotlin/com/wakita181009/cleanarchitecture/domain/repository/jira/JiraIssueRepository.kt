package com.wakita181009.cleanarchitecture.domain.repository.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.valueobject.Page
import com.wakita181009.cleanarchitecture.domain.valueobject.PageNumber
import com.wakita181009.cleanarchitecture.domain.valueobject.PageSize
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId

interface JiraIssueRepository {
    suspend fun findByIds(ids: List<JiraIssueId>): Either<JiraError, List<JiraIssue>>

    suspend fun list(
        pageNumber: PageNumber,
        pageSize: PageSize,
    ): Either<JiraError, Page<JiraIssue>>

    suspend fun bulkUpsert(issues: List<JiraIssue>): Either<JiraError, List<JiraIssue>>
}
