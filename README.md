# Clean Architecture Kotlin Sample

A sample Kotlin/Spring Boot WebFlux application demonstrating **Clean Architecture** (Hexagonal Architecture) with **functional programming** patterns.

## Highlights

- **Pure Kotlin Core** - Domain and Application layers have zero Spring dependencies
- **Functional Error Handling** - Arrow-kt `Either` monad for type-safe error propagation
- **Hexagonal Architecture** - Clear separation between business logic and infrastructure
- **Reactive Stack** - Spring WebFlux, R2DBC, Kotlin Coroutines

## Overview

This application showcases:

- **Clean Architecture** with proper layer separation and dependency inversion
- **Functional Programming** using Arrow-kt for monadic error handling
- **Framework Independence** - Business logic is testable without Spring context
- **Jira API Integration** - Background job that syncs Jira issues to PostgreSQL
- **GraphQL API** - Query endpoint for accessing synced data

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.3.0 |
| Framework | Spring Boot 3.5.4 (WebFlux) |
| Database | PostgreSQL + R2DBC + jOOQ |
| API | GraphQL Kotlin |
| Functional | Arrow-kt (Either, Raise DSL) |
| Testing | Kotest + MockK |

## Architecture

Hexagonal architecture with five modules:

```
framework → application → domain ← infrastructure
     ↑
presentation (GraphQL)
```

### Layer Dependencies

| Module | Spring | Description |
|--------|:------:|-------------|
| `domain/` | ❌ | Pure business logic - entities, value objects, ports |
| `application/` | ❌ | Use cases with transaction boundaries |
| `infrastructure/` | ✅ | API adapters, repository implementations |
| `presentation/` | ✅ | GraphQL queries, types, data loaders |
| `framework/` | ✅ | Spring Boot app, job runners, configuration |

**Domain and Application layers are pure Kotlin** - no `@Service`, `@Component`, or any Spring annotations. This enables:
- Unit testing without Spring context
- Framework-agnostic business logic
- True dependency inversion

### Error Handling Architecture

Two-layer error hierarchy with Arrow-kt `Either`:

```
DomainError (what happened)          ApplicationError (which operation failed)
├── JiraError                        ├── JiraIssueSyncError
│   ├── DatabaseError        →       │   ├── ProjectKeyFetchFailed(JiraError)
│   └── ApiError                     │   ├── IssueFetchFailed(JiraError)
└── ...                              │   └── IssuePersistFailed(TransactionError)
                                     └── ...
```

## Functional Programming Patterns

### Either Monad for Error Handling

```kotlin
// Domain layer: Define errors as sealed classes
sealed class JiraError : DomainError() {
    class DatabaseError(message: String) : JiraError()
    class ApiError(message: String) : JiraError()
}

// Repository: Wrap exceptions in Either
override suspend fun findByIds(ids: List<JiraIssueId>): Either<JiraError, List<JiraIssue>> =
    Either.catch {
        // database operation
    }.mapLeft { e ->
        JiraError.DatabaseError("Failed: ${e.message}")
    }

// UseCase: Compose with either DSL
override suspend fun execute(): Either<JiraIssueSyncError, Int> =
    either {
        val projectKeys = repository.findAll()
            .mapLeft(JiraIssueSyncError::FetchFailed)
            .bind()  // Short-circuit on error
        // continue processing...
    }
```

### TransactionExecutor Port Pattern

Transaction management abstracted via port interface (no Spring in application layer):

```kotlin
// Application port (pure Kotlin)
interface TransactionExecutor {
    suspend fun <T> executeInTransaction(block: suspend () -> Either<*, T>): Either<TransactionError, T>
}

// Infrastructure adapter (Spring)
@Component
class TransactionExecutorImpl(
    private val transactionalOperator: TransactionalOperator
) : TransactionExecutor { ... }
```

## Quick Start

```bash
# Start database
docker compose -f docker/compose.yml up -d

# Build
./gradlew build

# Run GraphQL server
./gradlew :framework:bootRun

# Run Jira sync job
./gradlew :framework:bootRun --args='--job=sync-jira-issue'
```

## Features

### Jira Issue Sync

Background job that fetches Jira issues and stores them in PostgreSQL:

```bash
./gradlew :framework:bootRun --args='--job=sync-jira-issue'
```

### GraphQL API

Query synced data via GraphQL:

```graphql
query {
  jiraIssue(id: "12345") {
    id
    key
    createdAt
    updatedAt
  }
}
```

GraphQL Playground: `http://localhost:8080/playground`

## Future Direction: CQRS

The current architecture naturally supports evolution to **CQRS** (Command Query Responsibility Segregation):

### Current State

```
application/usecase/
├── JiraIssueSyncUseCase        ← Command (writes data)
└── JiraIssueFindByIdsUseCase   ← Query (reads data)
```

### CQRS Evolution

```
application/
├── command/                    ← Write operations
│   └── SyncJiraIssuesCommand
├── query/                      ← Read operations
│   └── FindJiraIssuesByIdsQuery
└── model/
    ├── write/                  ← Write models (rich domain)
    └── read/                   ← Read models (optimized DTOs)
```

**Benefits of CQRS:**
- **Clear Intent** - Commands change state, Queries read state
- **Optimized Models** - Read models tailored for specific views
- **Scalability** - Independent scaling of read/write paths
- **Event Sourcing Ready** - Natural progression to event-driven architecture

## Environment Variables

Copy `.env.sample` to `.env` and configure:

| Variable | Description |
|----------|-------------|
| `POSTGRES_HOST` | Database host |
| `POSTGRES_PORT` | Database port |
| `POSTGRES_DATABASE` | Database name |
| `POSTGRES_USER` | Database user |
| `POSTGRES_PASSWORD` | Database password |
| `JIRA_API_TOKEN` | Base64 encoded `email:api_token` |

## Documentation

See `.claude/docs/` for detailed implementation guides:

- `architecture.md` - Clean architecture layer responsibilities
- `api-integration.md` - External API integration patterns
- `database.md` - jOOQ and Flyway patterns
- `domain-modeling.md` - Entity and value object patterns
- `job-runner.md` - Background job implementation
- `presentation.md` - GraphQL implementation

## License

MIT
