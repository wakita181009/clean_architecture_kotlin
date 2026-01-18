package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueSyncError

interface JiraIssueSyncUseCase {
    suspend fun execute(): Either<JiraIssueSyncError, Int>
}
