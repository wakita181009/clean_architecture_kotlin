# Coding Style

## Immutability (CRITICAL)

ALWAYS use immutable data structures, NEVER mutate:

```kotlin
// WRONG: Mutation
fun updateUser(user: User, name: String): User {
    user.name = name  // MUTATION!
    return user
}

// CORRECT: Immutability with data class copy()
fun updateUser(user: User, name: String): User {
    return user.copy(name = name)
}
```

## File Organization

MANY SMALL FILES > FEW LARGE FILES:
- High cohesion, low coupling
- 200-400 lines typical, 800 max
- Extract utilities from large classes
- Organize by feature/domain, not by type

## Error Handling

Use Arrow-kt `Either` for domain errors:

```kotlin
// GOOD: Either for expected errors
suspend fun findIssue(id: JiraIssueId): Either<JiraError, JiraIssue> {
    return Either.catch {
        repository.findById(id)
    }.mapLeft { error ->
        when (error) {
            is NotFoundException -> JiraError.NotFound(id)
            else -> JiraError.DatabaseError(error.message ?: "Unknown error")
        }
    }
}

// BAD: Throwing exceptions for expected errors
suspend fun findIssue(id: JiraIssueId): JiraIssue {
    return repository.findById(id) ?: throw NotFoundException()  // BAD
}
```

## Input Validation

Use Value Objects with validation in companion object:

```kotlin
@JvmInline
value class JiraIssueKey private constructor(val value: String) {
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

## Code Quality Checklist

Before marking work complete:
- [ ] Code is readable and well-named
- [ ] Functions are small (<50 lines)
- [ ] Files are focused (<800 lines)
- [ ] No deep nesting (>4 levels)
- [ ] Proper error handling with Either
- [ ] No println statements (use proper logging)
- [ ] No hardcoded values (use constants or config)
- [ ] No mutation (immutable patterns used)
- [ ] ktlint passes (`./gradlew ktlintCheck`)
