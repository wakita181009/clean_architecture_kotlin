---
name: job-runner
description: Background job runner pattern using Template Method. SyncJobRunner base class, concrete runner implementation, and execution patterns.
---

# Background Job Runner Pattern

Patterns for implementing background jobs (runners).

## Overview

Background jobs are implemented as Spring `ApplicationRunner` components that:

1. Execute on application startup
2. Are triggered by command-line arguments (`--job=<job-name>`)
3. Call use cases and exit with appropriate status codes
4. Are excluded from test execution

## Template Method Pattern with SyncJobRunner

The project uses a **Template Method Pattern** with an abstract `SyncJobRunner<E>` base class.

### Base Class: SyncJobRunner

```kotlin
// framework/runner/SyncJobRunner.kt
abstract class SyncJobRunner<E> : ApplicationRunner {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val JOB_OPTION = "job"
    }

    // Abstract properties that subclasses must define
    protected abstract val jobName: String
    protected abstract val entityName: String

    // Abstract method for the actual sync logic
    protected abstract suspend fun execute(): Either<E, Int>

    override fun run(args: ApplicationArguments) {
        if (!shouldRunJob(args)) {
            logger.debug("Skipping {} sync. Use --{}={} to enable.", entityName, JOB_OPTION, jobName)
            return
        }

        logger.info("Starting {} sync...", entityName)

        runBlocking {
            execute()
        }.fold(
            ifLeft = { error -> handleError(error) },
            ifRight = { count -> handleSuccess(count) },
        )
    }

    private fun shouldRunJob(args: ApplicationArguments): Boolean =
        args.getOptionValues(JOB_OPTION)?.firstOrNull() == jobName

    private fun handleSuccess(count: Int) {
        logger.info("{} sync completed successfully. Synced {} {}.",
            entityName.replaceFirstChar { it.uppercase() }, count, entityName)
        exitProcess(0)
    }

    private fun handleError(error: E) {
        logger.error("Error occurred during {} sync: {}", entityName, error)
        exitProcess(1)
    }
}
```

### Key Design Decisions

| Aspect | Decision | Benefit |
|--------|----------|---------|
| Generic type `<E>` | Error type parameter | Type-safe error handling per domain |
| `protected abstract val` | Abstract properties | Enforces required configuration |
| `protected abstract suspend fun` | Abstract method | Enables coroutine-based execution |
| `shouldRunJob()` private | Encapsulated logic | Consistent job filtering |
| Logging in base class | Centralized logging | Uniform log format |

## Concrete Runner Implementation

Concrete runners extend `SyncJobRunner` and only need to define:

1. `jobName` - The CLI argument value
2. `entityName` - Human-readable name for logging
3. `execute()` - The actual sync logic

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

## Benefits of Template Method Pattern

### Code Reduction

| Aspect | Before (Per Runner) | After (Per Runner) |
|--------|--------------------|--------------------|
| Lines of code | ~40 lines | ~10 lines |
| Companion object | Required | Not needed |
| Logging setup | Manual | Automatic |
| Error handling | Duplicated | Centralized |

### Consistency Guarantees

- **Uniform job filtering**: All runners use the same `--job` argument pattern
- **Consistent logging**: Same log format across all runners
- **Standard exit codes**: Exit 0 for success, Exit 1 for failure
- **Type-safe errors**: Generic `<E>` ensures proper error type handling

## Running Jobs

### Command Line

```bash
# Run Jira issue sync
./gradlew :framework:bootRun --args='--job=sync-jira-issue'
```

### Docker

```bash
docker run <image> --job=sync-jira-issue
```

## Testing Considerations

Runners do NOT use `@Profile("!test")` annotation. Instead, `SyncJobRunner.shouldRunJob()` checks for the `--job` argument, which won't be present during tests.

## Naming Conventions

| Component | Pattern | Example |
|-----------|---------|---------|
| Base class | `SyncJobRunner<E>` | Fixed name |
| Runner class | `{Entity}SyncRunner` | `JiraIssueSyncRunner` |
| Job name | `sync-{entity}` | `sync-jira-issue` |
| Entity name | Human-readable | `"Jira issue"` |
| UseCase | `{Entity}SyncUseCase` | `JiraIssueSyncUseCase` |

## File Organization

```
framework/src/main/kotlin/com/wakita181009/cleanarchitecture/framework/runner/
├── SyncJobRunner.kt           # Abstract base class (Template Method)
└── jira/
    └── JiraIssueSyncRunner.kt # Jira issue sync
```

## Implementation Checklist

When adding a new background job:

- [ ] Create runner class in `framework/runner/` extending `SyncJobRunner<ErrorType>`
- [ ] Define `override val jobName: String` with job CLI argument
- [ ] Define `override val entityName: String` for logging
- [ ] Implement `override suspend fun execute(): Either<ErrorType, Int>`
- [ ] Inject required UseCase via constructor
- [ ] Document the job name in CLAUDE.md

### What You DON'T Need To Do

Thanks to `SyncJobRunner`, you don't need to:

- Define companion object constants
- Implement `run(args: ApplicationArguments)` manually
- Handle job argument checking
- Implement logging (start, success, error)
- Handle `exitProcess` calls
- Create `runBlocking` wrapper
