package com.wakita181009.cleanarchitecture.infrastructure.repository.jira

import arrow.core.Either
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraProjectRepository
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectKey
import com.wakita181009.cleanarchitecture.infrastructure.postgres_gen.tables.references.JIRA_PROJECT
import kotlinx.coroutines.reactor.awaitSingle
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
class JiraProjectRepositoryImpl(
    private val dsl: DSLContext,
) : JiraProjectRepository {
    override suspend fun findAllProjectKeys(): Either<JiraError, List<JiraProjectKey>> =
        Either
            .catch {
                Flux
                    .from(
                        dsl
                            .selectFrom(JIRA_PROJECT),
                    ).map { record -> JiraProjectKey(record.key!!) }
                    .collectList()
                    .awaitSingle()
            }.mapLeft { e ->
                JiraError.DatabaseError(
                    message = "Failed to fetch project keys: ${e.message}",
                    cause = e,
                )
            }
}
