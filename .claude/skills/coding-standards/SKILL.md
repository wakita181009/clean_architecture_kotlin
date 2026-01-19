---
name: coding-standards
description: Universal coding standards, best practices, and patterns for Kotlin, Spring Boot WebFlux, and Clean Architecture development.
---

# Coding Standards & Best Practices

Universal coding standards applicable across Kotlin/Spring Boot projects.

> **Note**: For Clean Architecture patterns, see `skills/clean-architecture/SKILL.md`.
> For Testing standards, see `rules/testing.md`.

## Code Quality Principles

### 1. Readability First
- Code is read more than written
- Clear variable and function names
- Self-documenting code preferred over comments
- Consistent formatting (use ktlint)

### 2. KISS (Keep It Simple, Stupid)
- Simplest solution that works
- Avoid over-engineering
- No premature optimization
- Easy to understand > clever code

### 3. DRY (Don't Repeat Yourself)
- Extract common logic into functions
- Create reusable components
- Share utilities across modules
- Avoid copy-paste programming

### 4. YAGNI (You Aren't Gonna Need It)
- Don't build features before they're needed
- Avoid speculative generality
- Add complexity only when required
- Start simple, refactor when needed

## Kotlin Standards

### Variable Naming

```kotlin
// GOOD: Descriptive names
val jiraIssueKey = JiraIssueKey("PROJ-123")
val isUserAuthenticated = true
val totalIssueCount = 1000

// BAD: Unclear names
val k = "PROJ-123"
val flag = true
val x = 1000
```

### Function Naming

```kotlin
// GOOD: Verb-noun pattern
suspend fun fetchJiraIssue(id: JiraIssueId): Either<JiraError, JiraIssue>
fun calculateSimilarity(a: List<Double>, b: List<Double>): Double
fun isValidIssueKey(key: String): Boolean

// BAD: Unclear or noun-only
suspend fun issue(id: String): JiraIssue
fun similarity(a: List<Double>, b: List<Double>): Double
fun key(k: String): Boolean
```

### Immutability Pattern (CRITICAL)

```kotlin
// GOOD: Use data class copy()
val updatedIssue = issue.copy(
    status = JiraIssueStatus.DONE,
    updatedAt = Instant.now()
)

val updatedList = issues + newIssue

// BAD: Never mutate
issue.status = JiraIssueStatus.DONE  // BAD - won't compile with val
(issues as MutableList).add(newIssue)  // BAD - casting to mutable
```

### Error Handling (Arrow-kt Either)

```kotlin
// GOOD: Comprehensive error handling with Either
suspend fun fetchData(id: JiraIssueId): Either<JiraError, JiraIssue> {
    return Either.catch {
        repository.findById(id)
    }.mapLeft { error ->
        when (error) {
            is HttpException -> JiraError.ApiError(error.code, error.message)
            is IOException -> JiraError.NetworkError(error.message ?: "Network error")
            else -> JiraError.UnknownError(error.message ?: "Unknown error")
        }
    }.flatMap { issue ->
        issue?.right() ?: JiraError.NotFound(id).left()
    }
}

// BAD: No error handling
suspend fun fetchData(id: JiraIssueId): JiraIssue {
    return repository.findById(id)!!  // BAD - NPE risk
}
```

### Coroutines Best Practices

```kotlin
// GOOD: Parallel execution when possible
suspend fun fetchAllData(): Triple<List<JiraIssue>, List<JiraProject>, Stats> {
    return coroutineScope {
        val issuesDeferred = async { fetchIssues() }
        val projectsDeferred = async { fetchProjects() }
        val statsDeferred = async { fetchStats() }

        Triple(
            issuesDeferred.await(),
            projectsDeferred.await(),
            statsDeferred.await()
        )
    }
}

// BAD: Sequential when unnecessary
suspend fun fetchAllData(): Triple<List<JiraIssue>, List<JiraProject>, Stats> {
    val issues = fetchIssues()
    val projects = fetchProjects()  // Waits for issues unnecessarily
    val stats = fetchStats()        // Waits for projects unnecessarily
    return Triple(issues, projects, stats)
}
```

### Type Safety

```kotlin
// GOOD: Value objects for type safety
@JvmInline
value class JiraIssueId private constructor(val value: String) {
    companion object {
        operator fun invoke(value: String): JiraIssueId {
            require(value.isNotBlank()) { "JiraIssueId cannot be blank" }
            return JiraIssueId(value)
        }
    }
}

fun findById(id: JiraIssueId): JiraIssue?  // Can't pass wrong ID type

// BAD: Using primitives
fun findById(id: String): JiraIssue?  // Easy to pass wrong string
```

## Comments & Documentation

### When to Comment

```kotlin
// GOOD: Explain WHY, not WHAT
// Use exponential backoff to avoid overwhelming the Jira API during outages
val delay = minOf(1000L * 2.0.pow(retryCount).toLong(), 30_000L)

// GOOD: Document complex business logic
// Issues older than 90 days are considered stale and excluded from sync
val recentIssues = issues.filter { it.updatedAt > cutoffDate }

// BAD: Stating the obvious
// Increment counter by 1
count++

// Get the issue
val issue = repository.findById(id)
```

### KDoc for Public APIs

```kotlin
/**
 * Syncs Jira issues from the API to the local database.
 *
 * @return [SyncResult] containing the number of issues synced, or an error
 * @throws Nothing - errors are returned as [Either.Left]
 *
 * @sample
 * ```kotlin
 * val result = useCase.execute()
 * result.fold(
 *     ifLeft = { error -> logger.error("Sync failed: $error") },
 *     ifRight = { result -> logger.info("Synced ${result.saved} issues") }
 * )
 * ```
 */
suspend fun execute(): Either<JiraIssueSyncError, SyncResult>
```

## Code Smell Detection

Watch for these anti-patterns:

### 1. Long Functions
```kotlin
// BAD: Function > 50 lines
fun processIssueData() {
    // 100 lines of code
}

// GOOD: Split into smaller functions
fun processIssueData(): Either<Error, ProcessedData> {
    return validateData()
        .flatMap { transformData(it) }
        .flatMap { saveData(it) }
}
```

### 2. Deep Nesting
```kotlin
// BAD: 5+ levels of nesting
if (user != null) {
    if (user.isAdmin) {
        if (issue != null) {
            if (issue.isActive) {
                if (hasPermission) {
                    // Do something
                }
            }
        }
    }
}

// GOOD: Early returns or when expression
if (user == null) return
if (!user.isAdmin) return
if (issue == null) return
if (!issue.isActive) return
if (!hasPermission) return

// Do something
```

### 3. Magic Numbers
```kotlin
// BAD: Unexplained numbers
if (retryCount > 3) { }
delay(500)

// GOOD: Named constants
private const val MAX_RETRIES = 3
private const val DEBOUNCE_DELAY_MS = 500L

if (retryCount > MAX_RETRIES) { }
delay(DEBOUNCE_DELAY_MS)
```

**Remember**: Code quality is not negotiable. Clear, maintainable code enables rapid development and confident refactoring.
