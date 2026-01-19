---
name: jooq-database
description: Database patterns using jOOQ, R2DBC, and Flyway. Repository implementation, query patterns, enum mapping, and transaction management.
---

# Database Patterns

Database access patterns using jOOQ and Flyway.

## Overview

The project uses:
- **PostgreSQL** as the database
- **R2DBC** for reactive database connectivity
- **jOOQ** for type-safe SQL queries
- **Flyway** for database migrations

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
        └── tables/
            ├── records/    # Table record classes
            └── references/ # Table references (JIRA_ISSUE, JIRA_PROJECT)
```

**IMPORTANT**: DO NOT edit generated code manually - it will be overwritten.

## Repository Implementation Pattern

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
}
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
        dsl.batch(queries).toMono().awaitSingleOrNull()
        issues
    }.mapLeft { e ->
        JiraError.DatabaseError("Failed to bulk upsert issues: ${e.message}", e)
    }
```

## Enum Mapping

**CRITICAL**: Always use explicit `when` expressions instead of `valueOf()`:

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

**DO NOT use `valueOf()`** - no compile-time check when enum values change.

## Transaction Management

### TransactionExecutor Pattern

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

```sql
CREATE TABLE jira_issue
(
    id          BIGINT PRIMARY KEY,  -- ID from Jira API
    project_id  BIGINT NOT NULL REFERENCES jira_project (id),
    key         VARCHAR(50) NOT NULL UNIQUE,
    -- ...
);

-- FK index (always create for foreign keys)
CREATE INDEX idx_jira_issue_project_id ON jira_issue (project_id);
```

## Implementation Checklist

When adding a new table:

- [ ] Create migration file in `db/migration/` with proper version
- [ ] Define table with appropriate primary key strategy
- [ ] Add foreign key constraints where needed
- [ ] Create indexes for FK columns and frequently queried fields
- [ ] Run `./gradlew :infrastructure:jooqCodegen` to generate code
- [ ] Create repository implementation class
- [ ] Define `toRecord()` and `toDomain()` conversion methods
- [ ] **For enum fields**: Define explicit `when`-based conversion (NOT `valueOf()`)
- [ ] Use `Either.catch { }.mapLeft { }` for error handling
