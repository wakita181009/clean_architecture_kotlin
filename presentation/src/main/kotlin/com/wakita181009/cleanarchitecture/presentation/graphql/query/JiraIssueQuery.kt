package com.wakita181009.cleanarchitecture.presentation.graphql.query

import arrow.core.raise.either
import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.scalars.ID
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import com.expediagroup.graphql.server.operations.Query
import com.wakita181009.cleanarchitecture.application.usecase.jira.JiraIssueListUseCase
import com.wakita181009.cleanarchitecture.domain.valueobject.PageNumber
import com.wakita181009.cleanarchitecture.domain.valueobject.PageSize
import com.wakita181009.cleanarchitecture.presentation.graphql.types.JiraIssue
import com.wakita181009.cleanarchitecture.presentation.graphql.types.JiraIssueList
import graphql.schema.DataFetchingEnvironment
import org.springframework.stereotype.Controller
import java.util.concurrent.CompletableFuture

@Controller
class JiraIssueQuery(
    private val jiraIssueListUseCase: JiraIssueListUseCase,
) : Query {
    @Suppress("unused")
    @GraphQLDescription("Returns a JIRA issue.")
    fun jiraIssue(
        dfe: DataFetchingEnvironment,
        id: ID,
    ): CompletableFuture<JiraIssue> = dfe.getValueFromDataLoader("JiraIssueDataLoader", id)

    @Suppress("unused")
    @GraphQLDescription("Returns a list of JIRA issues.")
    suspend fun jiraIssues(
        dfe: DataFetchingEnvironment,
        @GraphQLDescription("Start from ${PageNumber.MIN_VALUE}.") pageNumber: Int = 1,
        @GraphQLDescription("${PageSize.MIN_VALUE} - ${PageSize.MAX_VALUE}") pageSize: Int = 100,
    ) = either {
        jiraIssueListUseCase
            .execute(pageNumber, pageSize)
            .map(JiraIssueList::fromDomain)
            .bind()
    }
}
