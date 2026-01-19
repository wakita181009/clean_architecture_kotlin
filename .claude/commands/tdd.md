---
description: Enforce test-driven development workflow. Scaffold interfaces, generate tests FIRST, then implement minimal code to pass. Ensure 80%+ coverage.
---

# TDD Command

This command invokes the **tdd-guide** agent to enforce test-driven development methodology.

## What This Command Does

1. **Scaffold Interfaces** - Define types/interfaces first
2. **Generate Tests First** - Write failing tests (RED)
3. **Implement Minimal Code** - Write just enough to pass (GREEN)
4. **Refactor** - Improve code while keeping tests green (REFACTOR)
5. **Verify Coverage** - Ensure 80%+ test coverage

## When to Use

Use `/tdd` when:
- Implementing new features
- Adding new functions/components
- Fixing bugs (write test that reproduces bug first)
- Refactoring existing code
- Building critical business logic

## TDD Cycle

```
RED → GREEN → REFACTOR → REPEAT

RED:      Write a failing test
GREEN:    Write minimal code to pass
REFACTOR: Improve code, keep tests passing
REPEAT:   Next feature/scenario
```

## Example Usage

```
User: /tdd I need a use case to find Jira issues by priority

Agent (tdd-guide):
# TDD Session: Find Jira Issues by Priority

## Step 1: Define Interface (SCAFFOLD)

```kotlin
// application/usecase/jira/JiraIssueFindByPriorityUseCase.kt
interface JiraIssueFindByPriorityUseCase {
    suspend fun execute(priority: JiraIssuePriority): Either<JiraIssueFindByPriorityError, List<JiraIssue>>
}
```

## Step 2: Write Failing Test (RED)

```kotlin
// application/src/test/kotlin/.../JiraIssueFindByPriorityUseCaseImplTest.kt
class JiraIssueFindByPriorityUseCaseImplTest : BehaviorSpec({
    val repository = mockk<JiraIssueRepository>()
    val useCase = JiraIssueFindByPriorityUseCaseImpl(repository)

    Given("a valid priority") {
        val priority = JiraIssuePriority.HIGH
        val expectedIssues = listOf(
            JiraIssue(
                id = JiraIssueId("12345"),
                key = JiraIssueKey("PROJ-123"),
                priority = priority,
                // ... other fields
            )
        )

        When("issues exist with that priority") {
            coEvery { repository.findByPriority(priority) } returns expectedIssues.right()

            Then("should return the issues") {
                val result = useCase.execute(priority)
                result.shouldBeRight() shouldBe expectedIssues
            }
        }

        When("no issues exist with that priority") {
            coEvery { repository.findByPriority(priority) } returns emptyList<JiraIssue>().right()

            Then("should return empty list") {
                val result = useCase.execute(priority)
                result.shouldBeRight() shouldBe emptyList()
            }
        }

        When("repository returns error") {
            val error = JiraError.DatabaseError("Connection failed")
            coEvery { repository.findByPriority(priority) } returns error.left()

            Then("should map to application error") {
                val result = useCase.execute(priority)
                result.shouldBeLeft()
            }
        }
    }
})
```

## Step 3: Run Tests - Verify FAIL

```bash
./gradlew :application:test --tests "*JiraIssueFindByPriorityUseCaseImplTest"

FAIL: Unresolved reference: JiraIssueFindByPriorityUseCaseImpl
```

Tests fail as expected. Ready to implement.

## Step 4: Implement Minimal Code (GREEN)

```kotlin
// application/usecase/jira/JiraIssueFindByPriorityUseCaseImpl.kt
class JiraIssueFindByPriorityUseCaseImpl(
    private val repository: JiraIssueRepository,
) : JiraIssueFindByPriorityUseCase {
    override suspend fun execute(
        priority: JiraIssuePriority
    ): Either<JiraIssueFindByPriorityError, List<JiraIssue>> {
        return repository.findByPriority(priority)
            .mapLeft { JiraIssueFindByPriorityError.from(it) }
    }
}
```

## Step 5: Run Tests - Verify PASS

```bash
./gradlew :application:test --tests "*JiraIssueFindByPriorityUseCaseImplTest"

BUILD SUCCESSFUL
3 tests passed
```

All tests passing!

## Step 6: Check Coverage

```bash
./gradlew koverHtmlReport
# Open build/reports/kover/html/index.html
```

TDD session complete!
```

## TDD Best Practices

**DO:**
- Write the test FIRST, before any implementation
- Run tests and verify they FAIL before implementing
- Write minimal code to make tests pass
- Refactor only after tests are green
- Add edge cases and error scenarios
- Use `coEvery` for mocking suspend functions

**DON'T:**
- Write implementation before tests
- Skip running tests after each change
- Write too much code at once
- Ignore failing tests
- Use `every` for suspend functions (use `coEvery`)

## Test Types to Include

**Unit Tests** (UseCase-level):
- Happy path scenarios
- Edge cases (empty lists, null values)
- Error conditions (Either.Left)
- Boundary values

**Integration Tests** (Repository-level):
- Database operations with Testcontainers
- External API calls with WireMock
- Transaction behavior

## Coverage Requirements

- **80% minimum** for all code
- **100% required** for:
  - Domain value objects
  - Use case logic
  - Error mapping

## Commands Reference

```bash
./gradlew test                                    # Run all tests
./gradlew :application:test                       # Run module tests
./gradlew test --tests "*UseCaseTest"             # Run matching tests
./gradlew koverHtmlReport                         # Generate coverage
```

## Related Agents

This command invokes the `tdd-guide` agent.
