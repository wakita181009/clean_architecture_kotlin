package com.wakita181009.cleanarchitecture.presentation.graphql.dataloader

import arrow.core.Either
import com.expediagroup.graphql.dataloader.KotlinDataLoader
import com.expediagroup.graphql.generator.extensions.get
import com.expediagroup.graphql.generator.scalars.ID
import com.wakita181009.cleanarchitecture.application.usecase.jira.JiraIssueFindByIdsUseCase
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId
import com.wakita181009.cleanarchitecture.presentation.graphql.types.JiraIssue
import graphql.GraphQLContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.Try
import org.springframework.stereotype.Component
import kotlin.coroutines.EmptyCoroutineContext

@Component
class JiraIssueDataLoader(
    private val jiraIssueFindByIdsUseCase: JiraIssueFindByIdsUseCase,
) : KotlinDataLoader<ID, JiraIssue> {
    override val dataLoaderName = "JiraIssueDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<ID, JiraIssue> =
        DataLoaderFactory.newMappedDataLoaderWithTry { ids, ble ->
            val coroutineScope = ble.getContext<GraphQLContext>()?.get<CoroutineScope>() ?: CoroutineScope(EmptyCoroutineContext)
            coroutineScope.future { loadIssues(ids) }
        }

    private suspend fun loadIssues(ids: Set<ID>): Map<ID, Try<JiraIssue>> {
        val parsedIds = ids.associateWith { JiraIssueId.of(it.value) }
        val validIds = parsedIds.values.mapNotNull { it.getOrNull() }
        return jiraIssueFindByIdsUseCase.execute(validIds).fold(
            ifLeft = { error -> ids.associateWith { Try.failed(error) } },
            ifRight = { domainIssues ->
                val issueMap = domainIssues.map(JiraIssue::fromDomain).associateBy { it.id }
                buildResultMap(parsedIds, issueMap)
            },
        )
    }

    private fun buildResultMap(
        parsedIds: Map<ID, Either<Throwable, JiraIssueId>>,
        issueMap: Map<ID, JiraIssue>,
    ): Map<ID, Try<JiraIssue>> =
        parsedIds.mapValues { (id, parseResult) ->
            parseResult.fold(
                ifLeft = { Try.failed(IllegalArgumentException("Invalid ID format: ${id.value}")) },
                ifRight = { jiraIssueId ->
                    issueMap[ID(jiraIssueId.value.toString())]
                        ?.let { Try.succeeded(it) }
                        ?: Try.failed(NoSuchElementException("Issue not found: ${id.value}"))
                },
            )
        }
}
