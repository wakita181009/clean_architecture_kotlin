# Domain Modeling Patterns

This document defines patterns for modeling domain concepts.

## Overview

Domain models are defined in the `domain/` module with no external dependencies (except Arrow-kt). The domain layer consists of:

- **Entities**: Objects with identity and lifecycle
- **Value Objects**: Immutable objects defined by their attributes
- **Enums**: Enumerated domain concepts with computed properties

## Value Object Patterns

### Standard Value Object (Private Constructor + Invoke)

Use `@JvmInline value class` with **private constructor** and **companion object invoke operator**:

```kotlin
// domain/valueobject/jira/JiraIssueId.kt
@JvmInline
value class JiraIssueId private constructor(
    val value: Long,
) {
    companion object {
        operator fun invoke(value: Long) = JiraIssueId(value)
    }
}

// domain/valueobject/jira/JiraIssueKey.kt
@JvmInline
value class JiraIssueKey private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String) = JiraIssueKey(value)
    }
}
```

**Benefits:**
- Zero runtime overhead (inline at compile time)
- Type safety (prevents mixing different IDs)
- **Private constructor**: Prevents direct instantiation, enables future validation
- **Invoke operator**: Same syntax as constructor (`JiraIssueId(123)`)
- **Extensibility**: Easy to add validation logic in `invoke()` later

**Usage (same as regular constructor):**
```kotlin
// In adapters/repositories - same syntax works
val id = JiraIssueId(record.id!!)
val key = JiraIssueKey("FT-123")
```

### Value Object with Validation (Future Pattern)

When validation is needed, add it to the `invoke` operator:

```kotlin
@JvmInline
value class JiraIssueKey private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String): JiraIssueKey {
            require(value.matches(Regex("^[A-Z]+-\\d+$"))) {
                "Invalid Jira issue key format: $value"
            }
            return JiraIssueKey(value)
        }
    }
}
```

### Value Object Naming Conventions

| Pattern | Example | Usage |
|---------|---------|-------|
| `{Entity}Id` | `JiraIssueId`, `JiraProjectId` | Primary identifiers |
| `{Entity}Key` | `JiraIssueKey`, `JiraProjectKey` | External/business keys |

## Enum Patterns

### Enum with Computed Properties

Add computed properties for domain logic that depends on the enum value:

```kotlin
// domain/valueobject/jira/JiraIssuePriority.kt
enum class JiraIssuePriority {
    HIGHEST,
    HIGH,
    MEDIUM,
    LOW,
    LOWEST,
    ;

    val isCritical: Boolean
        get() = this == HIGHEST
}

// domain/valueobject/jira/JiraIssueType.kt
enum class JiraIssueType {
    EPIC,
    STORY,
    TASK,
    SUBTASK,
    BUG,
    ;

    val isBug: Boolean get() = this == BUG
}
```

**Use Cases:**
- Business rules based on enum values
- Categorization logic
- Priority/severity checks

**Naming:**
- Use UPPERCASE for enum values (Kotlin convention for constants)
- Match domain terminology, not API response values

## Entity Patterns

### Entity with Domain Logic

Entities should contain domain logic as computed properties:

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
) {
    // Domain logic as computed property
    val isCriticalBug: Boolean
        get() = issueType.isBug && priority.isCritical
}
```

**Common Derived Properties:**

| Property | Derivation | Example |
|----------|------------|---------|
| `isCriticalBug` | `issueType.isBug && priority.isCritical` | Critical bug detection |

## Entity vs Value Object

| Aspect | Entity | Value Object |
|--------|--------|--------------|
| Identity | Has unique ID | Defined by attributes |
| Mutability | Can change over time | Immutable |
| Comparison | By ID | By all attributes |
| Example | `JiraIssue`, `JiraProject` | `JiraIssueId`, `JiraIssueKey` |

## File Organization

```
domain/src/main/kotlin/com/wakita181009/cleanarchitecture/domain/
├── entity/
│   └── jira/
│       ├── JiraIssue.kt      # Entity with isCriticalBug
│       └── JiraProject.kt
└── valueobject/
    └── jira/
        ├── JiraIssueId.kt        # Simple value object
        ├── JiraIssueKey.kt
        ├── JiraIssuePriority.kt  # Enum with isCritical
        ├── JiraIssueType.kt      # Enum with isBug
        ├── JiraProjectId.kt
        └── JiraProjectKey.kt
```

## Implementation Checklist

When adding a new domain concept:

- [ ] Determine if it's an Entity or Value Object
- [ ] For Value Objects:
  - [ ] Use `@JvmInline value class` with **private constructor**
  - [ ] Add `companion object` with `operator fun invoke()`
  - [ ] Follow naming convention: `{Entity}{Attribute}`
- [ ] For Enums:
  - [ ] Use UPPERCASE for values (Kotlin convention)
  - [ ] Add computed properties for domain logic
- [ ] For Entities:
  - [ ] Use `data class`
  - [ ] Reference Value Objects for typed fields (not primitives)
  - [ ] Add domain logic as computed properties
  - [ ] Keep entities immutable (no `var` properties)

## Anti-Patterns

### Don't: Use Primitives for Domain Concepts

```kotlin
// BAD: Primitives lose type safety
data class JiraIssue(
    val id: Long,           // Could be confused with other Longs
    val projectId: Long,    // No distinction from issue ID
)

// GOOD: Value objects provide type safety
data class JiraIssue(
    val id: JiraIssueId,
    val projectId: JiraProjectId,
)
```

### Don't: Put Business Logic in Infrastructure

```kotlin
// BAD: Business logic in repository
class JiraIssueRepositoryImpl : JiraIssueRepository {
    fun findCriticalBugs() =
        issues.filter { it.issueType == "Bug" && it.priority == "Highest" }
}

// GOOD: Business logic in domain entity
data class JiraIssue(...) {
    val isCriticalBug: Boolean get() = issueType.isBug && priority.isCritical
}

// Repository just returns data
class JiraIssueRepositoryImpl : JiraIssueRepository {
    fun findAll(): List<JiraIssue>
}
// UseCase filters using domain logic
val criticalBugs = issues.filter { it.isCriticalBug }
```

### Don't: Use String Enums

```kotlin
// BAD: String constants
const val PRIORITY_HIGHEST = "highest"
if (issue.priority == PRIORITY_HIGHEST) { ... }

// GOOD: Enum with computed property
enum class JiraIssuePriority {
    HIGHEST, HIGH, MEDIUM, LOW, LOWEST;
    val isCritical: Boolean get() = this == HIGHEST
}
if (issue.priority.isCritical) { ... }
```