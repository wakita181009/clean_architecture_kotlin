package com.wakita181009.cleanarchitecture.presentation.graphql.query

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.scalars.ID
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import com.expediagroup.graphql.server.operations.Query
import com.wakita181009.cleanarchitecture.presentation.graphql.types.JiraIssue
import graphql.schema.DataFetchingEnvironment
import org.springframework.stereotype.Controller
import java.util.concurrent.CompletableFuture

@Controller
class JiraIssueQuery : Query {
    @Suppress("unused")
    @GraphQLDescription("Returns a JIRA issue.")
    fun jiraIssue(
        dfe: DataFetchingEnvironment,
        id: ID,
    ): CompletableFuture<JiraIssue> = dfe.getValueFromDataLoader("JiraIssueDataLoader", id)
}
