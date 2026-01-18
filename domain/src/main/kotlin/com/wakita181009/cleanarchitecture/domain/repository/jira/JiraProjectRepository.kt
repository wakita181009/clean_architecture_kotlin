package com.wakita181009.cleanarchitecture.domain.repository.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectKey

interface JiraProjectRepository {
    suspend fun findAllProjectKeys(): Either<JiraError, List<JiraProjectKey>>
}
