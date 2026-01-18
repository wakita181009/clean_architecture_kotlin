package com.wakita181009.cleanarchitecture.presentation.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.wakita181009.cleanarchitecture.domain.valueobject.Page
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue as DomainJiraIssue

@GraphQLDescription("Host list type.")
data class JiraIssueList(
    val items: List<JiraIssue>,
    override val totalCount: Int,
) : PaginatedList {
    companion object {
        fun fromDomain(domain: Page<DomainJiraIssue>) =
            JiraIssueList(
                items = domain.items.map(JiraIssue::fromDomain),
                totalCount = domain.totalCount,
            )
    }
}
