# Presentation Layer Implementation Guide

This document defines the presentation layer implementation patterns using GraphQL Kotlin.

## Overview

The presentation layer exposes domain data through GraphQL API endpoints. It uses [graphql-kotlin](https://opensource.expediagroup.com/graphql-kotlin/) with Spring WebFlux.

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

Use `@Controller` annotation and implement `Query` interface:

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
- `@Suppress("unused")` to suppress IDE warnings (methods are called via reflection)
- `@GraphQLDescription` for schema documentation
- `DataFetchingEnvironment` to access DataLoaders
- Return `CompletableFuture<T>` when using DataLoader

## DataLoader Implementation

DataLoaders batch multiple requests to prevent N+1 query problems.

### Pattern with KotlinDataLoader

```kotlin
// presentation/graphql/dataloader/JiraIssueDataLoader.kt
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
        // 1. Parse and validate IDs
        val parsedIds = ids.associateWith { JiraIssueId.of(it.value) }
        val validIds = parsedIds.values.mapNotNull { it.getOrNull() }

        // 2. Execute UseCase and build result map
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

### Key Points

| Element | Description |
|---------|-------------|
| `KotlinDataLoader<K, V>` | Key type and Value type for the loader |
| `dataLoaderName` | Must match the name used in `getValueFromDataLoader()` |
| `newMappedDataLoaderWithTry` | Returns `Map<K, Try<V>>` - handles partial failures |
| `CoroutineScope` | Retrieved from GraphQL context for coroutine execution |
| `coroutineScope.future { }` | Bridges coroutines to `CompletableFuture` |
| `Try.succeeded(value)` / `Try.failed(error)` | Wraps success/failure for each ID |

### Error Handling in DataLoader

DataLoaderでは個別のIDごとにエラーハンドリングを行う：

```kotlin
// 1. ID解析時のエラー（無効なフォーマット）
parseResult.fold(
    ifLeft = { Try.failed(IllegalArgumentException("Invalid ID format: ${id.value}")) },
    ifRight = { ... }
)

// 2. UseCase全体のエラー（DB接続エラー等）
jiraIssueFindByIdsUseCase.execute(validIds).fold(
    ifLeft = { error -> ids.associateWith { Try.failed(error) } },  // 全IDにエラーを伝播
    ifRight = { ... }
)

// 3. 個別IDの検索結果エラー（見つからない場合）
issueMap[ID(jiraIssueId.value.toString())]
    ?.let { Try.succeeded(it) }
    ?: Try.failed(NoSuchElementException("Issue not found: ${id.value}"))
```

### Structure Pattern

DataLoaderは以下の構造を推奨：

1. `getDataLoader()` - DataLoaderFactoryの設定とCoroutineScope取得
2. `loadIssues()` - メインのロードロジック（suspend関数）
3. `buildResultMap()` - 結果マップの構築（ID解析結果と検索結果のマッピング）

## GraphQL Types

### Domain to GraphQL Type Conversion

Define GraphQL types as data classes with `fromDomain` companion function:

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
| Nullable | Nullable field | Direct |

### Import Alias for Domain Types

When GraphQL type has the same name as domain entity:

```kotlin
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue as DomainJiraIssue
```

### Paginated List Types

For paginated responses, implement `PaginatedList` interface:

```kotlin
// presentation/graphql/types/PaginatedList.kt
interface PaginatedList {
    val totalCount: Int
}

// presentation/graphql/types/JiraIssueList.kt
@GraphQLDescription("Jira issue list type.")
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
```

**Naming Convention**: `{Entity}List` for paginated list types.

## Arrow Either Integration

### EitherInstrumentation for Automatic Either Unwrapping

`EitherInstrumentation` automatically unwraps `Either<Error, T>` results from resolvers:

```kotlin
// presentation/graphql/EitherInstrumentation.kt
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

**Benefits:**
- Resolvers can return `Either<ApplicationError, T>` directly
- Left values are automatically thrown as GraphQL errors
- Right values are unwrapped and returned as data

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

### Registering Custom Scalars and Monad Resolution

```kotlin
// presentation/graphql/hooks/CustomSchemaGeneratorHooks.kt
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

### Available Extended Scalars

From `graphql-java-extended-scalars`:

| Scalar | Kotlin Type | Usage |
|--------|-------------|-------|
| `DateTime` | `OffsetDateTime` | ISO-8601 datetime |
| `Date` | `LocalDate` | ISO-8601 date |
| `Time` | `LocalTime` | ISO-8601 time |
| `Long` | `Long` | 64-bit integer |
| `BigDecimal` | `BigDecimal` | Arbitrary precision |

### SchemaGeneratorHooksProvider for Client Generation

For graphql-kotlin Gradle plugin (schema generation):

```kotlin
class CustomSchemaGeneratorHooksProvider : SchemaGeneratorHooksProvider {
    override fun hooks() = CustomSchemaGeneratorHooks(emptyList())
}
```

## Implementation Checklist

When adding new GraphQL queries:

- [ ] Create GraphQL type in `types/` with `fromDomain` companion function
- [ ] Create DataLoader in `dataloader/` implementing `KotlinDataLoader`
- [ ] Create Query in `query/` with `@Controller` annotation
- [ ] Use `@GraphQLDescription` for schema documentation
- [ ] Register custom scalars in `CustomSchemaGeneratorHooks` if needed

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Query class | `{Entity}Query` | `JiraIssueQuery` |
| DataLoader class | `{Entity}DataLoader` | `JiraIssueDataLoader` |
| DataLoader name | `"{Entity}DataLoader"` | `"JiraIssueDataLoader"` |
| GraphQL type | Same as domain entity | `JiraIssue` |
| Domain import alias | `Domain{Entity}` | `DomainJiraIssue` |