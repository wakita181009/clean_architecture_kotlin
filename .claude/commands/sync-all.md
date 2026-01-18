Run all sync jobs in the correct order.

Execute these jobs sequentially (each requires the previous to complete):

1. Sync Jira issues:
   `./gradlew :framework:bootRun --args='--job=sync-jira-issue'`

2. Sync GitHub repositories:
   `./gradlew :framework:bootRun --args='--job=sync-github-repository'`

3. Sync GitHub pull requests (depends on repositories):
   `./gradlew :framework:bootRun --args='--job=sync-github-pull-request'`

Report the total count of synced items for each job.
