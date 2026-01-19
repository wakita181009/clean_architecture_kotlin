# Testing Requirements

## Minimum Test Coverage: 80%

Test Types (ALL required):
1. **Unit Tests** - Individual functions, use cases, value objects (Kotest)
2. **Integration Tests** - Repository implementations, API clients (Kotest + Testcontainers)
3. **Architecture Tests** - Layer dependency verification (ArchUnit)

## Test Framework

This project uses:
- **Kotest** - Testing framework with behavior specs
- **MockK** - Mocking library for Kotlin
- **Testcontainers** - Integration testing with real databases

## Test-Driven Development

MANDATORY workflow:
1. Write test first (RED)
2. Run test - it should FAIL
3. Write minimal implementation (GREEN)
4. Run test - it should PASS
5. Refactor (IMPROVE)
6. Verify coverage (80%+)

## Test Structure (Kotest BehaviorSpec)

```kotlin
class JiraIssueFindByIdUseCaseImplTest : BehaviorSpec({
    val repository = mockk<JiraIssueRepository>()
    val useCase = JiraIssueFindByIdUseCaseImpl(repository)

    Given("a valid JiraIssueId") {
        val issueId = JiraIssueId("12345")
        val expectedIssue = JiraIssue(
            id = issueId,
            key = JiraIssueKey("PROJ-123"),
            // ...
        )

        When("the issue exists in repository") {
            coEvery { repository.findById(issueId) } returns expectedIssue.right()

            Then("should return the issue") {
                val result = useCase.execute(issueId)
                result.shouldBeRight() shouldBe expectedIssue
            }
        }

        When("the issue does not exist") {
            coEvery { repository.findById(issueId) } returns null.right()

            Then("should return NotFound error") {
                val result = useCase.execute(issueId)
                result.shouldBeLeft() shouldBe JiraIssueFindByIdError.NotFound(issueId)
            }
        }
    }
})
```

## Test Commands

```bash
# Run all tests
./gradlew test

# Run tests with coverage report
./gradlew koverHtmlReport

# Run specific module tests
./gradlew :application:test
./gradlew :infrastructure:test

# Run tests matching pattern
./gradlew test --tests "*UseCaseTest"
```

## Troubleshooting Test Failures

1. Use **tdd-guide** agent
2. Check test isolation
3. Verify mocks are correct (coEvery for suspend functions)
4. Fix implementation, not tests (unless tests are wrong)

## Agent Support

- **tdd-guide** - Use PROACTIVELY for new features, enforces write-tests-first
- **code-reviewer** - Review test quality and coverage
