# Refactor Clean

Safely identify and remove dead code with test verification:

1. Run dead code analysis:
   - Use IntelliJ inspections or Detekt
   - Search for unused imports: `./gradlew ktlintCheck`
   - Check for unused dependencies in `build.gradle.kts`

2. Identify unused code manually:
   - Search for functions with no callers
   - Find classes with no usages
   - Look for commented-out code blocks

3. Categorize findings by severity:
   - SAFE: Test files, unused utilities, private functions
   - CAUTION: Public APIs, interfaces, repository methods
   - DANGER: Config classes, Spring beans, main entry points

4. Propose safe deletions only

5. Before each deletion:
   - Run full test suite: `./gradlew test`
   - Verify tests pass
   - Apply change
   - Re-run tests
   - Rollback if tests fail

6. Show summary of cleaned items

Never delete code without running tests first!

## Kotlin-Specific Checks

### Unused Imports
```bash
./gradlew ktlintCheck  # Will report unused imports
./gradlew ktlintFormat # Will remove unused imports
```

### Unused Dependencies
Check `gradle/libs.versions.toml` and module `build.gradle.kts` files for:
- Libraries declared but not used
- Version catalog entries with no consumers

### Dead Code Patterns
- `@Suppress("unused")` annotations (review if still needed)
- Functions only called from deleted tests
- Repository methods never called from use cases
- Value objects with unused properties

## Clean Architecture Specific
- Verify ports are used by adapters
- Check use cases are wired in `UseCaseConfig`
- Ensure repositories have corresponding interfaces