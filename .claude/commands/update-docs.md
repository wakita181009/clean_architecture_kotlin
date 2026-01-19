# Update Documentation

Sync documentation from source-of-truth:

1. Read `build.gradle.kts` and `gradle/libs.versions.toml`
   - Extract dependencies and versions
   - Document tech stack

2. Read `.env.sample`
   - Extract all environment variables
   - Document purpose and format

3. Read existing `README.md` and `.claude/CLAUDE.md`
   - Identify outdated sections
   - Update command references

4. Update `.claude/docs/` files:
   - `architecture.md` - Layer structure and patterns
   - `database.md` - Schema and migration info
   - `api-integration.md` - External API patterns

5. Identify obsolete documentation:
   - Find docs not modified in 90+ days
   - List for manual review

6. Show diff summary

## Source of Truth

- `build.gradle.kts` - Dependencies and build configuration
- `gradle/libs.versions.toml` - Version catalog
- `.env.sample` - Environment variables
- `infrastructure/src/main/resources/db/migration/` - Database schema
- `domain/` - Entity and value object definitions

## Documentation Structure

```
.claude/
├── CLAUDE.md           # Main project instructions
├── docs/
│   ├── architecture.md # Clean Architecture guide
│   ├── database.md     # jOOQ and Flyway patterns
│   ├── presentation.md # GraphQL patterns
│   └── ...
└── rules/
    ├── coding-style.md
    ├── testing.md
    └── ...
```

## Commands

```bash
# Generate jOOQ code (updates database docs)
./gradlew :infrastructure:jooqCodegen

# Check for outdated dependencies
./gradlew dependencyUpdates
```
