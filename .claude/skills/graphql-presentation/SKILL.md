---
name: graphql-presentation
description: GraphQL presentation layer implementation. Query patterns, DataLoaders for N+1 prevention, type conversion, and Arrow Either integration.
---

# Presentation Layer Implementation Guide

GraphQL presentation layer implementation patterns using GraphQL Kotlin.

## Overview

```
presentation/
└── graphql/
    ├── query/        # GraphQL queries (@Controller)
    ├── types/        # GraphQL response types
    ├── dataloader/   # DataLoaders for N+1 prevention
    └── hooks/        # Custom schema generator hooks
```

## Query Implementation

### Basic Pattern

```kotlin
// presentation/graphql/query/JiraIssueQuery.kt
@Controller
class JiraIssueQuery : Query {
    @Suppress("unused")
    @GraphQLDescription("Returns a JIRA issue.")
    fun jiraIssue(
        dfe: DataFetchingEnvironment,
        id: ID,
    ): CompletableFuture<JiraIssue> = dfe.getValueFromDataLoader("JiraIssueDataLoader", id)
}
```

### Key Points

- `@Controller` (not `@Component`) for Spring WebFlux integration
- `@Suppress("unused")` to suppress IDE warnings (methods called via reflection)
- `@GraphQLDescription` for schema documentation
- Return `CompletableFuture<T>` when using DataLoader

## DataLoader Implementation

DataLoaders batch multiple requests to prevent N+1 query problems.

```kotlin
// presentation/graphql/dataloader/JiraIssueDataLoader.kt
@Component
class JiraIssueDataLoader(
    private val jiraIssueFindByIdsUseCase: JiraIssueFindByIdsUseCase,
) : KotlinDataLoader<ID, JiraIssue> {
    override val dataLoaderName = "JiraIssueDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<ID, JiraIssue> =
        DataLoaderFactory.newMappedDataLoaderWithTry { ids, ble ->
            val coroutineScope = ble.getContext<GraphQLContext>()?.get<CoroutineScope>()
                ?: CoroutineScope(EmptyCoroutineContext)
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
```

### DataLoader Key Points

| Element | Description |
|---------|-------------|
| `KotlinDataLoader<K, V>` | Key type and Value type for the loader |
| `dataLoaderName` | Must match the name used in `getValueFromDataLoader()` |
| `newMappedDataLoaderWithTry` | Returns `Map<K, Try<V>>` - handles partial failures |
| `coroutineScope.future { }` | Bridges coroutines to `CompletableFuture` |

## GraphQL Types

### Domain to GraphQL Type Conversion

```kotlin
// presentation/graphql/types/JiraIssue.kt
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
```

### Type Mapping Rules

| Domain Type | GraphQL Type | Conversion |
|-------------|--------------|------------|
| Value Object (ID) | `ID` | `ID(valueObject.value.toString())` |
| Value Object (String) | `String` | `.value` |
| `OffsetDateTime` | `DateTime` scalar | Direct (via CustomHooks) |
| Enum | GraphQL Enum | Direct (auto-mapped) |

### Import Alias for Domain Types

```kotlin
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue as DomainJiraIssue
```

## Arrow Either Integration

### EitherInstrumentation

Automatically unwraps `Either<Error, T>` results from resolvers:

```kotlin
@Component
class EitherInstrumentation : SimplePerformantInstrumentation() {
    override fun instrumentDataFetcher(
        dataFetcher: DataFetcher<*>?,
        parameters: InstrumentationFieldFetchParameters?,
        state: InstrumentationState?,
    ): DataFetcher<*> {
        return DataFetcher { environment ->
            when (val originalResult = dataFetcher?.get(environment)) {
                is Either<*, *> -> processEitherResult(originalResult)
                is CompletableFuture<*> -> originalResult.thenApply { result ->
                    if (result is Either<*, *>) processEitherResult(result) else result
                }
                else -> originalResult
            }
        }
    }

    private fun <R> processEitherResult(eitherResult: Either<*, R>): Any? =
        eitherResult
            .map { DataFetcherResult.newResult<Any>().data(it).build() }
            .getOrElse { throw it as Throwable }
}
```

### Query with Either Return Type

```kotlin
@Controller
class JiraIssueQuery(
    private val jiraIssueListUseCase: JiraIssueListUseCase,
) : Query {
    @GraphQLDescription("Returns a list of JIRA issues.")
    suspend fun jiraIssues(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ) = either {
        jiraIssueListUseCase
            .execute(pageNumber, pageSize)
            .map(JiraIssueList::fromDomain)
            .bind()
    }
}
```

## Custom Schema Generator Hooks

```kotlin
@Component
class CustomSchemaGeneratorHooks(
    federatedSchemaResolvers: List<FederatedTypeResolver>,
) : FederatedSchemaGeneratorHooks(federatedSchemaResolvers) {

    // Register custom scalar types
    override fun willGenerateGraphQLType(type: KType): GraphQLType? =
        when (type.classifier) {
            OffsetDateTime::class -> ExtendedScalars.DateTime
            else -> super.willGenerateGraphQLType(type)
        }

    // Handle Either monad - extract Right type for schema
    override fun willResolveMonad(type: KType): KType =
        when (type.classifier) {
            Either::class -> type.arguments.getOrNull(1)?.type ?: super.willResolveMonad(type)
            else -> super.willResolveMonad(type)
        }
}
```

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Query class | `{Entity}Query` | `JiraIssueQuery` |
| DataLoader class | `{Entity}DataLoader` | `JiraIssueDataLoader` |
| DataLoader name | `"{Entity}DataLoader"` | `"JiraIssueDataLoader"` |
| GraphQL type | Same as domain entity | `JiraIssue` |
| Domain import alias | `Domain{Entity}` | `DomainJiraIssue` |

## Implementation Checklist

When adding new GraphQL queries:

- [ ] Create GraphQL type in `types/` with `fromDomain` companion function
- [ ] Create DataLoader in `dataloader/` implementing `KotlinDataLoader`
- [ ] Create Query in `query/` with `@Controller` annotation
- [ ] Use `@GraphQLDescription` for schema documentation
- [ ] Register custom scalars in `CustomSchemaGeneratorHooks` if needed
