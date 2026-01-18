# Clean Architecture Kotlin Sample

Kotlin/Spring Boot WebFlux application demonstrating Clean Architecture (Hexagonal Architecture) patterns.

## Overview

This is a **sample application** that demonstrates:
- Clean Architecture implementation in Kotlin
- Jira API integration with background sync job
- GraphQL API endpoint for data querying
- Reactive programming with R2DBC and Coroutines

## Quick Commands

```bash
# Development
./gradlew build                    # Build project
./gradlew test                     # Run tests
./gradlew ktlintCheck              # Check code style
./gradlew ktlintFormat             # Fix code style

# Database
docker compose -f docker/compose.yml up -d    # Start PostgreSQL
docker compose -f docker/compose.yml down     # Stop PostgreSQL
./gradlew :infrastructure:jooqCodegen         # Regenerate jOOQ code

# Run Application (GraphQL Server)
./gradlew :framework:bootRun

# Background Job
./gradlew :framework:bootRun --args='--job=sync-jira-issue'
```

## Tech Stack

Kotlin 2.3.0 | Spring Boot 3.5.4 WebFlux | PostgreSQL + R2DBC + jOOQ | GraphQL Kotlin | Arrow-kt | Kotest + MockK

## Architecture (Hexagonal/Clean Architecture)

```
framework → application → domain ← infrastructure
     ↑
presentation (GraphQL)
```

| Module | Spring Boot | Responsibility |
|--------|-------------|---------------|
| `domain/` | ❌ None | Entities, value objects, ports (interfaces), repository interfaces |
| `application/` | ❌ None | Use cases, transaction boundaries (pure Kotlin) |
| `infrastructure/` | ✅ Yes | API adapters, repository implementations, jOOQ |
| `presentation/` | ✅ Yes | GraphQL queries, types, data loaders |
| `framework/` | ✅ Yes | Spring Boot app, job runners, configuration |

**Domain/Application are pure Kotlin** - no Spring annotations, enabling easy unit testing without Spring context.

## Features

### 1. Jira Issue Sync (Background Job)

Fetches Jira issues via API and stores them in PostgreSQL:
```bash
./gradlew :framework:bootRun --args='--job=sync-jira-issue'
```

### 2. GraphQL API

Query synced Jira issues:
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

Access GraphQL Playground at: `http://localhost:8080/playground`

## Critical Rules

1. **Pure Kotlin layers**: Domain and Application must have NO Spring Boot dependencies (no `@Service`, `@Component`, etc.)
2. **Never edit** `infrastructure/src/generated/` (jOOQ generated code)
3. **Error handling**: Two-layer errors with Arrow-kt `Either`:
   - Domain: `sealed class : DomainError` with `DatabaseError`/`ApiError`
   - Application: `sealed class : ApplicationError` wrapping domain errors
   - Infrastructure: `Either.catch { }.mapLeft { DomainError }` pattern
4. **Enum conversion**: Use explicit `when`, never `valueOf()`
5. **Enum naming**: Use UPPERCASE for enum values (e.g., `HIGHEST`, `HIGH`, `MEDIUM`)
6. **Value objects**: `@JvmInline value class` with `private constructor` + `companion object { operator fun invoke() }`
7. **Transactions**: `TransactionExecutor.executeInTransaction {}` in use cases (port in application, impl in infrastructure)
8. **UseCase pattern**: Interface + Impl pattern (e.g., `JiraIssueSyncUseCase` interface + `JiraIssueSyncUseCaseImpl` class)
9. **UseCase DI**: UseCases are wired via `@Configuration` in framework layer (`UseCaseConfig`), NOT `@Service` annotation

## Project Structure

```
├── domain/                    # Pure business logic (no dependencies)
│   ├── entity/jira/          # JiraIssue
│   ├── valueobject/jira/     # JiraIssueId, JiraIssueKey, JiraIssuePriority, JiraIssueType, etc.
│   ├── port/jira/            # JiraApiClient interface
│   ├── repository/jira/      # JiraIssueRepository, JiraProjectRepository interfaces
│   └── error/                # DomainError, JiraError
│
├── application/              # Use cases (Interface + Impl pattern)
│   ├── port/               # TransactionExecutor interface
│   ├── usecase/jira/        # JiraIssueSyncUseCase (interface), JiraIssueSyncUseCaseImpl (impl)
│   │                        # JiraIssueFindByIdsUseCase (interface), JiraIssueFindByIdsUseCaseImpl (impl)
│   └── error/               # ApplicationError, TransactionError, jira/JiraIssueSyncError, jira/JiraIssueFindByIdError
│
├── infrastructure/           # External integrations
│   ├── adapter/             # TransactionExecutorImpl
│   │   └── jira/           # JiraApiClientImpl, JiraApiDto
│   ├── repository/jira/     # JiraIssueRepositoryImpl, JiraProjectRepositoryImpl
│   ├── config/              # FlywayConfig, JooqConfig, OkHttpConfig
│   └── src/generated/       # jOOQ generated code (DO NOT EDIT)
│
├── presentation/             # API layer
│   └── graphql/
│       ├── query/           # JiraIssueQuery
│       ├── types/           # JiraIssue (GraphQL type)
│       ├── dataloader/      # JiraIssueDataLoader
│       └── hooks/           # CustomSchemaGeneratorHooks
│
└── framework/                # Spring Boot entry point
    ├── Application.kt
    ├── runner/              # SyncJobRunner (abstract base class)
    │   └── jira/           # JiraIssueSyncRunner
    ├── config/              # AdapterConfig, UseCaseConfig
    └── properties/          # AppProperties
```

## Environment Variables

Copy `.env.sample` to `.env`:

| Variable | Description |
|----------|-------------|
| `POSTGRES_HOST` | Database host |
| `POSTGRES_PORT` | Database port |
| `POSTGRES_DATABASE` | Database name |
| `POSTGRES_USER` | Database user |
| `POSTGRES_PASSWORD` | Database password |
| `JIRA_API_TOKEN` | Base64 of `email:api_token` |

## Documentation

Detailed implementation guides in `.claude/docs/`:
- `architecture.md` - Clean architecture layer responsibilities
- `presentation.md` - GraphQL queries, DataLoaders, custom hooks
- `api-integration.md` - External API integration patterns
- `database.md` - jOOQ and Flyway patterns
- `domain-modeling.md` - Entity and value object patterns
- `job-runner.md` - Background job implementation