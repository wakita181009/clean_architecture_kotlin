# Verification Command

Run comprehensive verification on current codebase state.

## Instructions

Execute verification in this exact order:

1. **Build Check**
   - Run: `./gradlew build`
   - If it fails, report errors and STOP

2. **Code Style Check**
   - Run: `./gradlew ktlintCheck`
   - Report all violations with file:line

3. **Test Suite**
   - Run: `./gradlew test`
   - Report pass/fail count
   - Run: `./gradlew koverHtmlReport` for coverage

4. **println Audit**
   - Search for `println` in source files (excluding test files)
   - Report locations
   - Should use proper logging instead

5. **Git Status**
   - Show uncommitted changes
   - Show files modified since last commit

## Output

Produce a concise verification report:

```
VERIFICATION: [PASS/FAIL]

Build:     [OK/FAIL]
Ktlint:    [OK/X violations]
Tests:     [X/Y passed]
Coverage:  [Z%]
Println:   [OK/X found]

Ready for PR: [YES/NO]
```

If any critical issues, list them with fix suggestions.

## Arguments

$ARGUMENTS can be:
- `quick` - Only build + ktlint
- `full` - All checks (default)
- `pre-commit` - Checks relevant for commits
- `pre-pr` - Full checks plus security scan

## Commands Reference

```bash
./gradlew build          # Full build with tests
./gradlew ktlintCheck    # Code style check
./gradlew ktlintFormat   # Auto-fix code style
./gradlew test           # Run tests
./gradlew koverHtmlReport # Coverage report
```