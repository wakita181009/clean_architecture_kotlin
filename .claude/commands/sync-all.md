Run all sync jobs in the correct order.

Execute these jobs sequentially:

1. Sync Jira issues:
   ```bash
   ./gradlew :framework:bootRun --args='--job=sync-jira-issue'
   ```

Report the total count of synced items for each job.

## Notes

- Ensure PostgreSQL is running: `docker compose -f docker/compose.yml up -d`
- Ensure environment variables are set (see `.env.sample`)
- Each job runs independently and can be retried if failed

## Adding New Sync Jobs

When new sync jobs are added to the project, update this command:

1. Add the job to this list in dependency order
2. Jobs that depend on other data should run after their dependencies
3. Document any specific requirements for each job

## Example Output

```
SYNC REPORT
===========

Jira Issues:
  - Synced: 150 issues
  - New: 10
  - Updated: 140
  - Errors: 0

Total Time: 45s
Status: SUCCESS
```
