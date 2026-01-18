Review recent commits and update .claude/ documentation if needed.

## Steps

1. Check recent commits:
   ```bash
   git log --oneline -10
   git diff HEAD~${ARGUMENTS:-1}
   ```

2. Analyze changes for documentation impact:
   - New patterns or conventions introduced
   - Changes to existing patterns (value objects, entities, etc.)
   - New commands, jobs, or API integrations added
   - Critical rules affected
   - New dependencies or tech stack changes

3. If updates needed, edit the appropriate files:
   - `.claude/CLAUDE.md` - Quick reference, critical rules
   - `.claude/docs/architecture.md` - Layer structure, module responsibilities
   - `.claude/docs/api-integration.md` - External API patterns
   - `.claude/docs/database.md` - jOOQ, Flyway patterns
   - `.claude/docs/domain-modeling.md` - Entity, value object patterns
   - `.claude/docs/job-runner.md` - Background job patterns
   - `.claude/docs/selective-sync-pattern.md` - Data sync patterns

4. Report what was updated or confirm "No documentation updates needed."

## Usage

- `/update-docs` - Check last commit (default)
- `/update-docs 3` - Check last 3 commits
- `/update-docs abc123` - Check specific commit
