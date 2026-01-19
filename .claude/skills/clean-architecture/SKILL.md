---
name: clean-architecture
description: Clean Architecture implementation guide for Kotlin/Spring Boot. Layer structure, UseCase patterns, transaction management, and dependency rules.
---

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
│   └── jira/         # JiraIssuePort
├── repository/       # Data access interfaces
│   └── jira/         # JiraIssueRepository, JiraProjectRepository
└── error/            # Domain-specific errors (DomainError, JiraError)
```

### 2. Application Layer (`application/`)

**Responsibility**: Use case implementation, transaction boundary definition, application-specific error handling.

#### Framework Dependency Decision

This project uses **Interface + Impl pattern** for UseCases:

- `JiraIssueSyncUseCase` (interface) - defines the contract
- `JiraIssueSyncUseCaseImpl` (class) - contains implementation

**NOT allowed in Application layer:**
- `@Service` annotation (DI is handled in framework layer via `@Configuration`)
- Spring Web/WebFlux types
- Framework-specific concerns

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

### 3. Infrastructure Layer (`infrastructure/`)

**Responsibility**: External system integration implementation.

```
infrastructure/
├── adapter/          # Port implementations
│   ├── TransactionExecutorImpl.kt
│   └── jira/         # JiraIssueAdapterImpl, JiraApiDto
├── repository/       # Repository implementations
│   └── jira/         # JiraIssueRepositoryImpl, JiraProjectRepositoryImpl
├── config/           # FlywayConfig, JooqConfig, OkHttpConfig
└── src/generated/    # jOOQ generated code (do not edit)
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

## UseCase Implementation Pattern

```kotlin
// application/usecase/jira/JiraIssueSyncUseCase.kt (Interface)
interface JiraIssueSyncUseCase {
    suspend fun execute(): Either<JiraIssueSyncError, Int>
}

// application/usecase/jira/JiraIssueSyncUseCaseImpl.kt (Implementation)
class JiraIssueSyncUseCaseImpl(
    private val jiraProjectRepository: JiraProjectRepository,
    private val jiraIssueRepository: JiraIssueRepository,
    private val jiraIssuePort: JiraIssuePort,
    private val transactionExecutor: TransactionExecutor,
) : JiraIssueSyncUseCase {
    override suspend fun execute(): Either<JiraIssueSyncError, Int> =
        either {
            // 1. Data retrieval (outside transaction)
            val projectKeys = jiraProjectRepository
                .findAllProjectKeys()
                .mapLeft(JiraIssueSyncError::ProjectKeyFetchFailed)
                .bind()

            // 2. External API call (outside transaction)
            var totalCount = 0
            jiraIssuePort.fetchIssues(projectKeys, since).collect { result ->
                result.onRight { issues ->
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

// framework/config/UseCaseConfig.kt (DI Configuration)
@Configuration
open class UseCaseConfig(
    private val jiraProjectRepository: JiraProjectRepository,
    private val jiraIssueRepository: JiraIssueRepository,
    private val jiraIssuePort: JiraIssuePort,
    private val transactionExecutor: TransactionExecutor,
) {
    @Bean
    open fun jiraIssueSyncUseCase() =
        JiraIssueSyncUseCaseImpl(
            jiraProjectRepository = jiraProjectRepository,
            jiraIssueRepository = jiraIssueRepository,
            jiraIssuePort = jiraIssuePort,
            transactionExecutor = transactionExecutor,
        )
}
```

## Transaction Management

### TransactionExecutor Pattern

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `TransactionExecutor` (interface) | Application | Defines transaction contract |
| `TransactionExecutorImpl` | Infrastructure | Implements using `TransactionalOperator` |
| `TransactionError` | Application | Wraps transaction execution failures |

### Rules

| Layer | Transaction | Reason |
|-------|-------------|--------|
| Domain | No | Pure business rules |
| Application | Yes | UseCase = Transaction boundary via `TransactionExecutor` |
| Infrastructure | Maybe | Acceptable for single repository operations |
| Framework | No | Entry point only |

## Error Hierarchy

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

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| UseCase Interface | `{Entity}SyncUseCase` | `JiraIssueSyncUseCase` |
| UseCase Impl | `{UseCase}Impl` | `JiraIssueSyncUseCaseImpl` |
| Port (Domain) | `{Entity}Port` | `JiraIssuePort` |
| Port (Application) | `{Concern}Executor` | `TransactionExecutor` |
| Adapter | `{Entity}AdapterImpl` | `JiraIssueAdapterImpl` |
| Repository Interface | `{Entity}Repository` | `JiraIssueRepository` |
| Repository Impl | `{Repository}Impl` | `JiraIssueRepositoryImpl` |

## Implementation Checklist

When implementing new features:

- [ ] Domain: Define Entity using `data class`
- [ ] Domain: Define Value Objects using `@JvmInline value class`
- [ ] Domain: Define Port interfaces
- [ ] Domain: Define Repository interfaces
- [ ] Domain: Define domain errors as `sealed class : DomainError`
- [ ] Application: Define UseCase interface
- [ ] Application: Implement UseCase with `{UseCase}Impl` class (NO `@Service`)
- [ ] Application: Use `TransactionExecutor.executeInTransaction {}` for transactions
- [ ] Infrastructure: Create Port/Repository implementations
- [ ] Framework: Add UseCase bean to `UseCaseConfig`
