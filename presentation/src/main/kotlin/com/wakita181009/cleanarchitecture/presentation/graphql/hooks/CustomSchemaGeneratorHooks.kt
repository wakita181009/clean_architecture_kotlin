package com.wakita181009.cleanarchitecture.presentation.graphql.hooks

import arrow.core.Either
import com.expediagroup.graphql.generator.federation.FederatedSchemaGeneratorHooks
import com.expediagroup.graphql.generator.federation.execution.FederatedTypeResolver
import com.expediagroup.graphql.plugin.schema.hooks.SchemaGeneratorHooksProvider
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLType
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import kotlin.reflect.KType

/**
 * Used for graphql-kotlin-spring-client
 */
class CustomSchemaGeneratorHooksProvider : SchemaGeneratorHooksProvider {
    override fun hooks() = CustomSchemaGeneratorHooks(emptyList())
}

@Component
class CustomSchemaGeneratorHooks(
    federatedSchemaResolvers: List<FederatedTypeResolver>,
) : FederatedSchemaGeneratorHooks(federatedSchemaResolvers) {
    /**
     * Register additional GraphQL scalar types.
     */
    override fun willGenerateGraphQLType(type: KType): GraphQLType? =
        when (type.classifier) {
            OffsetDateTime::class -> ExtendedScalars.DateTime
            else -> super.willGenerateGraphQLType(type)
        }

    override fun willResolveMonad(type: KType): KType =
        when (type.classifier) {
            Either::class -> type.arguments.getOrNull(1)?.type ?: super.willResolveMonad(type)
            else -> super.willResolveMonad(type)
        }
}
