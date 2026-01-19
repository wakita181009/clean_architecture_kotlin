# Build and Fix

Incrementally fix Kotlin and build errors:

1. Run build: `./gradlew build`

2. Parse error output:
   - Group by file
   - Sort by severity (compilation errors first, then warnings)

3. For each error:
   - Show error context (5 lines before/after)
   - Explain the issue
   - Propose fix
   - Apply fix
   - Re-run build
   - Verify error resolved

4. Stop if:
   - Fix introduces new errors
   - Same error persists after 3 attempts
   - User requests pause

5. Show summary:
   - Errors fixed
   - Errors remaining
   - New errors introduced

Fix one error at a time for safety!

## Common Kotlin/Gradle Build Errors

### Compilation Errors
- Type mismatch: Check expected vs actual types
- Unresolved reference: Check imports and dependencies
- Suspend function errors: Ensure coroutine context

### jOOQ Errors
- Missing generated classes: Run `./gradlew :infrastructure:jooqCodegen`
- Table/column not found: Check Flyway migrations

### Dependency Errors
- Version conflicts: Check `gradle/libs.versions.toml`
- Missing dependencies: Run `./gradlew dependencies`

## Commands Reference

```bash
./gradlew build                    # Full build
./gradlew compileKotlin            # Compile only
./gradlew :module:compileKotlin    # Compile specific module
./gradlew ktlintFormat             # Fix code style
```