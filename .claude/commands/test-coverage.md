# Test Coverage

Analyze test coverage and generate missing tests:

1. Run tests with coverage:
   ```bash
   ./gradlew test
   ./gradlew koverHtmlReport
   ```

2. Analyze coverage report:
   - Open `build/reports/kover/html/index.html`
   - Check each module's coverage

3. Identify files below 80% coverage threshold

4. For each under-covered file:
   - Analyze untested code paths
   - Generate unit tests using Kotest BehaviorSpec
   - Generate integration tests with Testcontainers
   - Focus on domain and application layers

5. Verify new tests pass:
   ```bash
   ./gradlew test
   ```

6. Show before/after coverage metrics

7. Ensure project reaches 80%+ overall coverage

## Focus Areas

**Happy Path Scenarios:**
- Normal execution flow
- Expected inputs and outputs

**Error Handling:**
- Either.Left cases
- Exception mapping
- Invalid input handling

**Edge Cases:**
- Empty collections
- Null values (nullable types)
- Boundary values

**Boundary Conditions:**
- Min/max values for value objects
- Empty strings
- Date edge cases

## Test Structure (Kotest BehaviorSpec)

```kotlin
class MyUseCaseImplTest : BehaviorSpec({
    val repository = mockk<MyRepository>()
    val useCase = MyUseCaseImpl(repository)

    Given("valid input") {
        When("repository returns success") {
            Then("should return expected result") {
                // test
            }
        }

        When("repository returns error") {
            Then("should map to application error") {
                // test
            }
        }
    }
})
```

## Commands Reference

```bash
./gradlew test                           # Run all tests
./gradlew :module:test                   # Run module tests
./gradlew koverHtmlReport                # Generate HTML report
./gradlew koverVerify                    # Verify coverage threshold
./gradlew test --tests "*UseCaseTest"    # Run specific tests
```