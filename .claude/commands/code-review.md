# Code Review

Comprehensive security and quality review of uncommitted changes:

1. Get changed files: `git diff --name-only HEAD`

2. For each changed file, check for:

**Security Issues (CRITICAL):**
- Hardcoded credentials, API keys, tokens
- SQL injection vulnerabilities (raw SQL strings)
- Missing input validation
- Insecure dependencies
- Secrets in logs

**Code Quality (HIGH):**
- Functions > 50 lines
- Files > 800 lines
- Nesting depth > 4 levels
- Missing error handling (Either not used)
- println statements (use logger instead)
- TODO/FIXME comments
- Missing KDoc for public APIs

**Best Practices (MEDIUM):**
- Mutation patterns (use immutable data class copy())
- Emoji usage in code/comments
- Missing tests for new code
- Blocking calls in suspend functions

**Clean Architecture (HIGH):**
- Domain layer with Spring annotations
- Application layer with Spring annotations
- Infrastructure concerns in domain
- Direct repository calls bypassing use cases

3. Generate report with:
   - Severity: CRITICAL, HIGH, MEDIUM, LOW
   - File location and line numbers
   - Issue description
   - Suggested fix

4. Block commit if CRITICAL or HIGH issues found

Never approve code with security vulnerabilities!

## Kotlin-Specific Checks

- Use `val` over `var` (immutability)
- Prefer `data class` with `copy()` for mutations
- Use `sealed class` for exhaustive when expressions
- Ensure `when` on enums is exhaustive (no `else` branch)
- Check for proper `suspend` function usage
- Verify `Either` is used for error handling

## Clean Architecture Checks

- Domain layer: No Spring annotations, pure Kotlin
- Application layer: No Spring annotations, uses ports
- Infrastructure layer: Implements domain interfaces
- Framework layer: Spring configuration only
