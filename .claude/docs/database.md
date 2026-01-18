# Database Patterns

This document defines database access patterns using jOOQ and Flyway.

## Overview

The project uses:
- **PostgreSQL** as the database
- **R2DBC** for reactive database connectivity
- **jOOQ** for type-safe SQL queries
- **Flyway** for database migrations

## jOOQ Configuration

### Configuration Class

```kotlin
// infrastructure/config/JooqConfig.kt
@Configuration
@EnableTransactionManagement
class JooqConfig(
    private val cfi: ConnectionFactory,
) {
    @Bean
    fun dsl(): DSLContext = DSL.using(cfi).dsl()

    @Bean
    fun transactionalOperator(): TransactionalOperator =
        TransactionalOperator.create(R2dbcTransactionManager(cfi))
}
```

### Key Components

| Bean | Purpose |
|------|---------|
| `DSLContext` | jOOQ entry point for building type-safe SQL |
| `TransactionalOperator` | Reactive transaction management for R2DBC (used internally by `TransactionExecutorImpl`) |

## Code Generation

### Running jOOQ Codegen

```bash
# Requires running PostgreSQL database
./gradlew :infrastructure:jooqCodegen
```

### Generated Code Location

```
infrastructure/
└── src/generated/kotlin/
    └── com/wakita181009/cleanarchitecture/infrastructure/postgres_gen/
        ├── enums/          # PostgreSQL enum types
        │   ├── JiraIssuePriorityEnum.kt
        │   └── JiraIssueTypeEnum.kt
        └── tables/
            ├── records/    # Table record classes
            │   ├── JiraIssueRecord.kt
            │   └── JiraProjectRecord.kt
            └── references/ # Table references
                └── Tables.kt (JIRA_ISSUE, JIRA_PROJECT)
```

### Important Notes

- **DO NOT edit generated code manually** - it will be overwritten
- Generated code is excluded from ktlint checks via filter configuration
- Re-run codegen after schema changes

## Repository Implementation Pattern

### Basic Structure

```kotlin
@Repository
class JiraIssueRepositoryImpl(
    private val dsl: DSLContext,
) : JiraIssueRepository {

    companion object {
        // Domain -> Record conversion
        private fun JiraIssue.toRecord(): JiraIssueRecord =
            JiraIssueRecord(
                id = id.value,
                projectId = projectId.value,
                key = key.value,
                summary = summary,
                description = description?.let { JSONB.jsonb(it) },
                issueType = issueType.toDbEnum(),
                priority = priority.toDbEnum(),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

        // Record -> Domain conversion
        private fun JiraIssueRecord.toDomain(): JiraIssue =
            JiraIssue(
                id = JiraIssueId(id!!),
                projectId = JiraProjectId(projectId!!),
                key = JiraIssueKey(key!!),
                summary = summary!!,
                description = description?.data(),
                issueType = issueType!!.toDomain(),
                priority = priority!!.toDomain(),
                createdAt = createdAt!!,
                updatedAt = updatedAt!!,
            )
    }

    // Repository methods...
}
```

### Conversion Pattern

Always define conversion methods in companion object:

| Method | Direction | Location |
|--------|-----------|----------|
| `Entity.toRecord()` | Domain → jOOQ Record | Companion object |
| `Record.toDomain()` | jOOQ Record → Domain | Companion object |

### Handling Nullable Fields

jOOQ record fields are nullable by default. Use `!!` for required fields when converting to domain:

```kotlin
private fun JiraIssueRecord.toDomain(): JiraIssue =
    JiraIssue(
        id = JiraIssueId(id!!),  // Required field
        key = JiraIssueKey(key!!),
        // ...
    )
```

## Query Patterns

### SELECT All Records

```kotlin
override suspend fun findAll(): Either<JiraError, List<JiraIssue>> =
    Either.catch {
        Flux
            .from(dsl.selectFrom(JIRA_ISSUE))
            .map { it.toDomain() }
            .collectList()
            .awaitSingleOrNull() ?: emptyList()
    }.mapLeft { e ->
        JiraError.DatabaseError("Failed to fetch issues: ${e.message}", e)
    }
```

### SELECT with Condition

```kotlin
override suspend fun findByIds(
    ids: List<JiraIssueId>
): Either<JiraError, List<JiraIssue>> =
    Either.catch {
        Flux
            .from(
                dsl.selectFrom(JIRA_ISSUE)
                    .where(JIRA_ISSUE.ID.`in`(ids.map { it.value }))
            )
            .map { it.toDomain() }
            .collectList()
            .awaitSingleOrNull() ?: emptyList()
    }.mapLeft { e ->
        JiraError.DatabaseError("Failed to fetch issues by IDs: ${e.message}", e)
    }
```

### Bulk Upsert (INSERT ON CONFLICT)

```kotlin
override suspend fun bulkUpsert(
    issues: List<JiraIssue>
): Either<JiraError, List<JiraIssue>> =
    Either.catch {
        if (issues.isEmpty()) return@catch emptyList()

        val records = issues.map { it.toRecord() }
        val queries = records.map { record ->
            dsl
                .insertInto(JIRA_ISSUE)
                .set(record)
                .onConflict(JIRA_ISSUE.ID)
                .doUpdate()
                .set(JIRA_ISSUE.PROJECT_ID, record.projectId)
                .set(JIRA_ISSUE.KEY, record.key)
                .set(JIRA_ISSUE.SUMMARY, record.summary)
                .set(JIRA_ISSUE.DESCRIPTION, record.description)
                .set(JIRA_ISSUE.ISSUE_TYPE, record.issueType)
                .set(JIRA_ISSUE.PRIORITY, record.priority)
                .set(JIRA_ISSUE.UPDATED_AT, record.updatedAt)
        }
        dsl
            .batch(queries)
            .toMono()
            .awaitSingleOrNull()
        issues
    }.mapLeft { e ->
        JiraError.DatabaseError("Failed to bulk upsert issues: ${e.message}", e)
    }
```

### Key Upsert Pattern Details

1. **Early return for empty list**: Avoid unnecessary database calls
2. **Convert to records**: `issues.map { it.toRecord() }`
3. **Build individual queries**: Map over records to create INSERT...ON CONFLICT queries
4. **Batch execution**: Use `dsl.batch(queries)` for performance
5. **Convert to Mono**: Use `toMono()` from reactor-kotlin-extensions
6. **Await result**: Use `awaitSingleOrNull()` for coroutine integration

## Enum Mapping

### PostgreSQL Enum Definition

```sql
CREATE TYPE jira_issue_priority AS ENUM (
    'HIGHEST',
    'HIGH',
    'MEDIUM',
    'LOW',
    'LOWEST'
);
```

### Domain Enum

```kotlin
enum class JiraIssuePriority {
    HIGHEST, HIGH, MEDIUM, LOW, LOWEST;
}
```

### Conversion in Repository

**IMPORTANT**: Always use explicit `when` expressions instead of `valueOf()`:

```kotlin
companion object {
    // Domain enum -> jOOQ enum (CORRECT: explicit when)
    private fun JiraIssueType.toDbEnum(): JiraIssueTypeEnum =
        when (this) {
            JiraIssueType.EPIC -> JiraIssueTypeEnum.EPIC
            JiraIssueType.STORY -> JiraIssueTypeEnum.STORY
            JiraIssueType.TASK -> JiraIssueTypeEnum.TASK
            JiraIssueType.SUBTASK -> JiraIssueTypeEnum.SUBTASK
            JiraIssueType.BUG -> JiraIssueTypeEnum.BUG
        }

    private fun JiraIssuePriority.toDbEnum(): JiraIssuePriorityEnum =
        when (this) {
            JiraIssuePriority.HIGHEST -> JiraIssuePriorityEnum.HIGHEST
            JiraIssuePriority.HIGH -> JiraIssuePriorityEnum.HIGH
            JiraIssuePriority.MEDIUM -> JiraIssuePriorityEnum.MEDIUM
            JiraIssuePriority.LOW -> JiraIssuePriorityEnum.LOW
            JiraIssuePriority.LOWEST -> JiraIssuePriorityEnum.LOWEST
        }

    // jOOQ enum -> Domain enum (CORRECT: explicit when)
    private fun JiraIssueTypeEnum.toDomain(): JiraIssueType =
        when (this) {
            JiraIssueTypeEnum.EPIC -> JiraIssueType.EPIC
            JiraIssueTypeEnum.STORY -> JiraIssueType.STORY
            JiraIssueTypeEnum.TASK -> JiraIssueType.TASK
            JiraIssueTypeEnum.SUBTASK -> JiraIssueType.SUBTASK
            JiraIssueTypeEnum.BUG -> JiraIssueType.BUG
        }
}
```

### Anti-pattern: valueOf()

**DO NOT use `valueOf()` for enum conversion**:

```kotlin
// BAD: valueOf() is not compile-time safe
issueType = JiraIssueTypeEnum.valueOf(issueType.name)  // Don't do this!
```

Problems with `valueOf()`:
- No compile-time check when enum values change
- Runtime `IllegalArgumentException` if names don't match exactly
- Implicit coupling between enum names across layers

## JSONB Handling

### Storing JSONB

```kotlin
description = description?.let { JSONB.jsonb(it) }
```

### Reading JSONB

```kotlin
description = record.description?.data()  // Returns String?
```

## Flyway Migrations

### Migration Location

```
infrastructure/src/main/resources/db/migration/
```

### Naming Convention

```
V{major}.{minor}.{patch}__{description}.sql
```

Examples:
- `V1.0.0__create_table_script.sql`
- `V1.0.1__populate_init_data.sql`

### Schema Design Patterns

#### Table with External ID (Primary Key from External System)

```sql
CREATE TABLE jira_issue
(
    id          BIGINT PRIMARY KEY,  -- ID from Jira API
    project_id  BIGINT NOT NULL REFERENCES jira_project (id),
    key         VARCHAR(50) NOT NULL UNIQUE,
    -- ...
);
```

#### Index Patterns

```sql
-- FK index (always create for foreign keys)
CREATE INDEX idx_jira_issue_project_id ON jira_issue (project_id);

-- Unique constraint index (automatically created)
key VARCHAR(50) NOT NULL UNIQUE

-- Lookup field index
CREATE INDEX idx_jira_issue_key ON jira_issue (key);

-- Enum field index (for filtering)
CREATE INDEX idx_jira_issue_type ON jira_issue (issue_type);
CREATE INDEX idx_jira_issue_priority ON jira_issue (priority);
```

## Transaction Management

### TransactionExecutor Pattern

Transaction management uses the port pattern to abstract the infrastructure details:

```kotlin
// application/port/TransactionExecutor.kt
interface TransactionExecutor {
    suspend fun <T> executeInTransaction(block: suspend () -> Either<*, T>): Either<TransactionError, T>
}

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
                transactionalOperator.executeAndAwait { block() }
            }.mapLeft { e ->
                TransactionError.ExecutionFailed("Transaction execution failed: ${e.message}", e)
            }.flatMap { result ->
                result.mapLeft { domainError ->
                    TransactionError.ExecutionFailed("Transaction failed due to domain error", domainError as? Throwable)
                }
            }
}
```

### Using TransactionExecutor in UseCase

```kotlin
class JiraIssueSyncUseCaseImpl(
    private val transactionExecutor: TransactionExecutor,
    // ...
) : JiraIssueSyncUseCase {
    override suspend fun execute(): Either<JiraIssueSyncError, Int> = either {
        // Outside transaction: API calls
        apiClient.fetchData().collect { result ->
            result.onRight { data ->
                // Inside transaction: Database operations
                transactionExecutor
                    .executeInTransaction {
                        repository.bulkUpsert(data)
                    }.mapLeft(JiraIssueSyncError::IssuePersistFailed)
                    .bind()
            }.bind()
        }
    }
}
```

### Key Points

- `TransactionExecutor` is an application port, `TransactionExecutorImpl` is in infrastructure
- The executor wraps both execution exceptions and domain errors into `TransactionError`
- UseCase maps `TransactionError` to application-specific errors (e.g., `IssuePersistFailed`)

## Reactive Extensions

### Required Imports

```kotlin
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toMono
```

### Common Patterns

| Pattern | Usage |
|---------|-------|
| `Flux.from(publisher)` | Convert jOOQ Publisher to Reactor Flux |
| `.map { }` | Transform each record |
| `.collectList()` | Collect Flux to List |
| `.awaitSingleOrNull()` | Await single result in coroutine |
| `.toMono()` | Convert jOOQ batch result to Mono |

## Implementation Checklist

When adding a new table:

- [ ] Create migration file in `db/migration/` with proper version
- [ ] Define table with appropriate primary key strategy
- [ ] Add foreign key constraints where needed
- [ ] Create indexes for FK columns and frequently queried fields
- [ ] Run `./gradlew :infrastructure:jooqCodegen` to generate code
- [ ] Create repository implementation class
- [ ] Define `toRecord()` and `toDomain()` conversion methods
- [ ] **For enum fields**: Define explicit `when`-based conversion functions (NOT `valueOf()`)
- [ ] Implement repository interface methods
- [ ] Use `Either.catch { }.mapLeft { }` for error handling
- [ ] Use `TransactionExecutor.executeInTransaction {}` in UseCase for transaction management