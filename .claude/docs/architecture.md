# Clean Architecture Implementation Guide

This document defines the clean architecture implementation guidelines for this sample project.

## Layer Structure

```
┌─────────────────────────────────────────────────────────┐
│                    Framework Layer                       │
│         (Controllers, Runners, Spring Config)            │
└────────────────────────┬────────────────────────────────┘
                         │ calls
                         ▼
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                      │
│              (Use Cases, Orchestration)                  │
│                 Transaction Boundary                     │
└────────────────────────┬────────────────────────────────┘
                         │ uses
                         ▼
┌─────────────────────────────────────────────────────────┐
│                     Domain Layer                         │
│     (Entities, Value Objects, Ports, Repositories)       │
│                  Pure Business Logic                     │
└─────────────────────────────────────────────────────────┘
                         ▲
                         │ implements
┌─────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                     │
│        (Adapters, Repository Impl, External APIs)        │
└─────────────────────────────────────────────────────────┘
```

## Framework Dependency by Layer

Following Clean Architecture principles, **domain and application layers are pure Kotlin** with no framework dependencies:

| Layer | Spring Boot | Reason |
|-------|-------------|--------|
| Domain | ❌ None | Pure business logic, no external dependencies |
| Application | ❌ None | Pure use case orchestration, ports abstract infrastructure |
| Infrastructure | ✅ Yes | DI (`@Component`, `@Repository`), `TransactionalOperator` |
| Presentation | ✅ Yes | GraphQL Kotlin Spring integration, `@Controller` |
| Framework | ✅ Yes | Spring Boot entry point, `@Configuration`, `@Bean` |

This ensures:
- **Testability**: Domain/Application can be unit tested without Spring context
- **Portability**: Core business logic is framework-agnostic
- **Maintainability**: Framework changes don't affect business rules

## Layer Responsibilities

### 1. Domain Layer (`domain/`)

**Responsibility**: Pure business logic with no external dependencies.

```
domain/
├── entity/           # Domain entities
│   └── jira/         # JiraIssue
├── valueobject/      # Value objects
│   └── jira/         # JiraIssueId, JiraIssueKey, JiraIssuePriority, JiraIssueType, etc.
├── port/             # External service interfaces
│   └── jira/         # JiraApiClient
├── repository/       # Data access interfaces
│   └── jira/         # JiraIssueRepository, JiraProjectRepository
└── error/            # Domain-specific errors (DomainError, JiraError)
```

#### Entity Implementation

```kotlin
// domain/entity/jira/JiraIssue.kt
data class JiraIssue(
    val id: JiraIssueId,
    val projectId: JiraProjectId,
    val key: JiraIssueKey,
    val summary: String,
    val description: String?,
    val issueType: JiraIssueType,
    val priority: JiraIssuePriority,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
```

#### Value Object Implementation

Use `@JvmInline value class` for simple value objects:

```kotlin
// domain/valueobject/jira/JiraIssueId.kt
@JvmInline
value class JiraIssueId(val value: Long)

// domain/valueobject/jira/JiraIssueKey.kt
@JvmInline
value class JiraIssueKey(val value: String)
```

Use `enum class` with computed properties for domain enumerations:

```kotlin
// domain/valueobject/jira/JiraIssuePriority.kt
enum class JiraIssuePriority {
    HIGHEST, HIGH, MEDIUM, LOW, LOWEST;
}

// domain/valueobject/jira/JiraIssueType.kt
enum class JiraIssueType {
    EPIC, STORY, TASK, SUBTASK, BUG;
}
```

#### Port Definition

Use `Flow` for streaming data with pagination:

```kotlin
// domain/port/jira/JiraApiClient.kt
interface JiraApiClient {
    fun fetchIssues(
        projectKeys: List<JiraProjectKey>,
        since: OffsetDateTime,
    ): Flow<Either<JiraError, List<JiraIssue>>>
}
```

#### Repository Interface

```kotlin
// domain/repository/jira/JiraProjectRepository.kt
interface JiraProjectRepository {
    suspend fun findAllProjectKeys(): Either<JiraError, List<JiraProjectKey>>
}

// domain/repository/jira/JiraIssueRepository.kt
interface JiraIssueRepository {
    suspend fun findByIds(ids: List<JiraIssueId>): Either<JiraError, List<JiraIssue>>
    suspend fun bulkUpsert(issues: List<JiraIssue>): Either<JiraError, List<JiraIssue>>
}
```

#### Error Definition

Domain errors use `sealed class` extending `DomainError` (which extends `Exception`):

```kotlin
// domain/error/DomainError.kt
abstract class DomainError(
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)

// domain/error/JiraError.kt
sealed class JiraError(
    message: String?,
    cause: Throwable? = null,
) : DomainError(message, cause) {
    class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : JiraError(message, cause)

    class ApiError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : JiraError(message, cause)
}
```

### 2. Application Layer (`application/`)

**Responsibility**: Use case implementation, transaction boundary definition, application-specific error handling.

#### Framework Dependency Decision

This project uses **Interface + Impl pattern** for UseCases to maintain clean architecture boundaries:

**UseCase Pattern:**
- `JiraIssueSyncUseCase` (interface) - defines the contract
- `JiraIssueSyncUseCaseImpl` (class) - contains implementation

**Allowed in Impl classes:**
- `TransactionExecutor` port for transaction management (defined in application layer, implemented in infrastructure)

**NOT allowed in Application layer:**
- `@Service` annotation (DI is handled in framework layer via `@Configuration`)
- Spring Web/WebFlux types (`ServerRequest`, `Mono`, `Flux`)
- Spring Security types
- Framework-specific concerns (routing, serialization config)

```
application/
├── port/             # Application ports (interfaces for infrastructure)
│   └── TransactionExecutor.kt
├── error/            # Application-specific errors
│   ├── ApplicationError.kt
│   ├── TransactionError.kt
│   └── jira/
│       ├── JiraIssueSyncError.kt
│       └── JiraIssueFindByIdError.kt
└── usecase/          # Application use cases (Interface + Impl)
    └── jira/
        ├── JiraIssueSyncUseCase.kt       # Interface
        ├── JiraIssueSyncUseCaseImpl.kt   # Implementation
        ├── JiraIssueFindByIdsUseCase.kt      # Interface
        └── JiraIssueFindByIdsUseCaseImpl.kt  # Implementation
```

#### Application Error Definition

Application errors wrap domain errors and describe **which operation failed**:

```kotlin
// application/error/ApplicationError.kt
abstract class ApplicationError(
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)

// application/error/TransactionError.kt
sealed class TransactionError(
    message: String?,
    cause: Throwable?,
) : ApplicationError(message, cause) {
    class ExecutionFailed(
        message: String?,
        cause: Throwable?,
    ) : TransactionError(message, cause)
}

// application/error/jira/JiraIssueSyncError.kt
sealed class JiraIssueSyncError(
    message: String?,
    cause: Throwable?,
) : ApplicationError(message, cause) {
    class ProjectKeyFetchFailed(cause: JiraError) : JiraIssueSyncError("Failed to fetch project keys", cause)
    class IssueFetchFailed(cause: JiraError) : JiraIssueSyncError("Failed to fetch issues", cause)
    class IssuePersistFailed(cause: TransactionError) : JiraIssueSyncError("Failed to persist issues", cause)
}
```

**Error hierarchy:**
```
Exception
├── DomainError (domain layer - what happened)
│   └── JiraError
│       ├── DatabaseError
│       └── ApiError
└── ApplicationError (application layer - which operation failed)
    ├── TransactionError
    │   └── ExecutionFailed
    └── JiraIssueSyncError
        ├── ProjectKeyFetchFailed (wraps JiraError)
        ├── IssueFetchFailed (wraps JiraError)
        └── IssuePersistFailed (wraps TransactionError)
```

#### UseCase Implementation Rules

1. **1 UseCase = 1 Business Operation**
2. **Interface + Impl pattern** (interface defines contract, Impl contains logic)
3. **Transactions are managed in UseCase Impl**
4. **External API calls should NOT be inside transactions**
5. **Use `Flow.collect {}` to process streaming data**
6. **No `@Service` annotation** - DI is configured in framework layer

```kotlin
// application/port/TransactionExecutor.kt
interface TransactionExecutor {
    suspend fun <T> executeInTransaction(block: suspend () -> Either<*, T>): Either<TransactionError, T>
}

// application/usecase/jira/JiraIssueSyncUseCase.kt (Interface)
interface JiraIssueSyncUseCase {
    suspend fun execute(): Either<JiraIssueSyncError, Int>
}

// application/usecase/jira/JiraIssueSyncUseCaseImpl.kt (Implementation)
class JiraIssueSyncUseCaseImpl(
    private val jiraProjectRepository: JiraProjectRepository,
    private val jiraIssueRepository: JiraIssueRepository,
    private val jiraApiClient: JiraApiClient,
    private val transactionExecutor: TransactionExecutor,
) : JiraIssueSyncUseCase {
    override suspend fun execute(): Either<JiraIssueSyncError, Int> =
        either {
            // 1. Data retrieval (outside transaction)
            val projectKeys = jiraProjectRepository
                .findAllProjectKeys()
                .mapLeft(JiraIssueSyncError::ProjectKeyFetchFailed)
                .bind()

            // 2. External API call with streaming (outside transaction)
            var totalCount = 0
            jiraApiClient
                .fetchIssues(projectKeys, since)
                .collect { result ->
                    result
                        .onRight { issues ->
                            // 3. Data persistence (inside transaction per batch)
                            transactionExecutor
                                .executeInTransaction {
                                    jiraIssueRepository.bulkUpsert(issues)
                                }.mapLeft(JiraIssueSyncError::IssuePersistFailed)
                                .bind()
                            totalCount += issues.size
                        }.mapLeft(JiraIssueSyncError::IssueFetchFailed)
                        .bind()
                }
            totalCount
        }
}
```

#### UseCase DI Configuration (Framework Layer)

UseCases are wired in the framework layer using `@Configuration`:

```kotlin
// framework/config/UseCaseConfig.kt
@Configuration
open class UseCaseConfig(
    private val jiraProjectRepository: JiraProjectRepository,
    private val jiraIssueRepository: JiraIssueRepository,
    private val jiraApiClient: JiraApiClient,
    private val transactionExecutor: TransactionExecutor,
) {
    @Bean
    open fun jiraIssueSyncUseCase() =
        JiraIssueSyncUseCaseImpl(
            jiraProjectRepository = jiraProjectRepository,
            jiraIssueRepository = jiraIssueRepository,
            jiraApiClient = jiraApiClient,
            transactionExecutor = transactionExecutor,
        )
}
```

### 3. Infrastructure Layer (`infrastructure/`)

**Responsibility**: External system integration implementation.

```
infrastructure/
├── adapter/          # Port implementations
│   ├── TransactionExecutorImpl.kt  # Transaction port implementation
│   └── jira/         # JiraApiClientImpl, JiraApiDto
├── repository/       # Repository implementations
│   └── jira/         # JiraIssueRepositoryImpl, JiraProjectRepositoryImpl
├── config/           # FlywayConfig, JooqConfig, OkHttpConfig
└── src/generated/    # jOOQ generated code (do not edit)
```

#### TransactionExecutor Implementation

```kotlin
// infrastructure/adapter/TransactionExecutorImpl.kt
@Component
class TransactionExecutorImpl(
    private val transactionalOperator: TransactionalOperator,
) : TransactionExecutor {
    override suspend fun <T> executeInTransaction(
        block: suspend () -> Either<*, T>
    ): Either<TransactionError, T> =
        Either
            .catch {
                transactionalOperator.executeAndAwait {
                    block()
                }
            }.mapLeft { e ->
                TransactionError.ExecutionFailed(
                    message = "Transaction execution failed: ${e.message}",
                    cause = e,
                )
            }.flatMap { result ->
                result.mapLeft { domainError ->
                    TransactionError.ExecutionFailed(
                        message = "Transaction execution failed due to domain error",
                        cause = domainError as? Throwable,
                    )
                }
            }
}
```

#### Repository Implementation with jOOQ

Use `Either.catch { }.mapLeft { }` pattern:

```kotlin
@Repository
class JiraIssueRepositoryImpl(
    private val dsl: DSLContext,
) : JiraIssueRepository {

    override suspend fun bulkUpsert(issues: List<JiraIssue>): Either<JiraError, List<JiraIssue>> =
        Either
            .catch {
                // jOOQ operations
            }.mapLeft { e ->
                JiraError.DatabaseError(
                    message = "Failed to bulk upsert issues: ${e.message}",
                    cause = e,
                )
            }
}
```

### 4. Framework Layer (`framework/`)

**Responsibility**: Application entry point and DI configuration.

```
framework/
├── Application.kt    # Spring Boot main class
├── config/           # AdapterConfig, UseCaseConfig
├── properties/       # AppProperties
└── runner/           # SyncJobRunner (abstract base class)
    └── jira/         # JiraIssueSyncRunner
```

#### Runner Implementation (Template Method Pattern)

```kotlin
// framework/runner/jira/JiraIssueSyncRunner.kt
@Component
class JiraIssueSyncRunner(
    private val jiraIssueSyncUseCase: JiraIssueSyncUseCase,
) : SyncJobRunner<JiraIssueSyncError>() {
    override val jobName: String = "sync-jira-issue"
    override val entityName: String = "Jira issue"
    override suspend fun execute(): Either<JiraIssueSyncError, Int> = jiraIssueSyncUseCase.execute()
}
```

### 5. Presentation Layer (`presentation/`)

**Responsibility**: API endpoints (GraphQL).

```
presentation/
└── graphql/
    ├── query/        # JiraIssueQuery
    ├── types/        # JiraIssue (GraphQL type)
    ├── dataloader/   # JiraIssueDataLoader
    └── hooks/        # CustomSchemaGeneratorHooks
```

## Transaction Management

### TransactionExecutor Pattern

Transaction management uses the **port pattern** to keep the application layer framework-agnostic:

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `TransactionExecutor` (interface) | Application | Defines transaction contract |
| `TransactionExecutorImpl` | Infrastructure | Implements using `TransactionalOperator` |
| `TransactionError` | Application | Wraps transaction execution failures |

### Rules

| Layer        | Transaction | Reason                                        |
|--------------|-------------|-----------------------------------------------|
| Domain       | No          | Pure business rules                           |
| Application  | Yes         | UseCase = Transaction boundary via `TransactionExecutor` |
| Infrastructure | Maybe     | Acceptable for single repository operations   |
| Framework    | No          | Entry point only                              |

### Usage in UseCase

```kotlin
// In UseCase implementation
transactionExecutor
    .executeInTransaction {
        repository.bulkUpsert(data)
    }.mapLeft(SomeError::PersistFailed)
    .bind()
```

The `TransactionExecutor.executeInTransaction` method:
- Executes the block within a transaction
- Returns `Either<TransactionError, T>`
- Handles both execution exceptions and domain errors from the block

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| UseCase Interface | `{Entity}SyncUseCase` | `JiraIssueSyncUseCase` |
| UseCase Impl | `{UseCase}Impl` | `JiraIssueSyncUseCaseImpl` |
| Port (Domain) | `{Service}Client` or `{Service}ApiClient` | `JiraApiClient` |
| Port (Application) | `{Concern}Executor` | `TransactionExecutor` |
| Adapter | `{Port}Impl` | `JiraApiClientImpl`, `TransactionExecutorImpl` |
| Repository Interface | `{Entity}Repository` | `JiraIssueRepository` |
| Repository Impl | `{Repository}Impl` | `JiraIssueRepositoryImpl` |
| Value Object | Singular with prefix | `JiraIssueId`, `JiraProjectKey` |
| Entity | Singular with prefix | `JiraIssue` |
| DTO | `{Purpose}Request` / `{Purpose}Response` | `JiraSearchRequest` |
| Domain Error | `{Domain}Error` (sealed class) | `JiraError` |
| Application Error | `{Entity}SyncError` or `{Concern}Error` (sealed class) | `JiraIssueSyncError`, `TransactionError` |
| UseCase Config | `UseCaseConfig` | `UseCaseConfig` |

## Implementation Checklist

When implementing new features:

- [ ] Domain: Define Entity using `data class`
- [ ] Domain: Define Value Objects using `@JvmInline value class` or `enum class`
- [ ] Domain: Define necessary Port interfaces (use `Flow` for streaming)
- [ ] Domain: Define Repository interfaces
- [ ] Domain: Define domain errors as `sealed class : DomainError`
- [ ] Application: Define application errors as `sealed class : ApplicationError`
- [ ] Application: Define UseCase interface with `suspend fun execute()` method
- [ ] Application: Implement UseCase with `{UseCase}Impl` class (NO `@Service` annotation)
- [ ] Application: Map domain errors to application errors using `mapLeft`
- [ ] Application: Use `TransactionExecutor.executeInTransaction {}` for transaction boundaries
- [ ] Infrastructure: Create Port implementations with `Either.catch { }.mapLeft { ApiError }`
- [ ] Infrastructure: Create Repository implementations with `Either.catch { }.mapLeft { DatabaseError }`
- [ ] Infrastructure: Add database migrations in `db/migration/`
- [ ] Framework: Add UseCase bean to `UseCaseConfig` (inject `TransactionExecutor` if needed)
- [ ] Framework: Implement Runner with application error type
- [ ] Framework: Add configuration properties if needed