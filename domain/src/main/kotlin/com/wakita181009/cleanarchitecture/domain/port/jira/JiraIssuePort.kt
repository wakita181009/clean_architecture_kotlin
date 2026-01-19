package com.wakita181009.cleanarchitecture.domain.port.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectKey
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime

interface JiraIssuePort {
    fun fetchIssues(
        projectKeys: List<JiraProjectKey>,
        since: OffsetDateTime,
    ): Flow<Either<JiraError, List<JiraIssue>>>
}
