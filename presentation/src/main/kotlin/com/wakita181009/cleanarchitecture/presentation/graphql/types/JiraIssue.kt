package com.wakita181009.cleanarchitecture.presentation.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.scalars.ID
import java.time.OffsetDateTime
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue as DomainJiraIssue

@GraphQLDescription("JIRA issue.")
data class JiraIssue(
    val id: ID,
    val key: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromDomain(domain: DomainJiraIssue) =
            JiraIssue(
                id = ID(domain.id.value.toString()),
                key = domain.key.value,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt,
            )
    }
}
