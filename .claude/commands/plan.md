---
description: Restate requirements, assess risks, and create step-by-step implementation plan. WAIT for user CONFIRM before touching any code.
---

# Plan Command

This command invokes the **planner** agent to create a comprehensive implementation plan before writing any code.

## What This Command Does

1. **Restate Requirements** - Clarify what needs to be built
2. **Identify Risks** - Surface potential issues and blockers
3. **Create Step Plan** - Break down implementation into phases
4. **Wait for Confirmation** - MUST receive user approval before proceeding

## When to Use

Use `/plan` when:
- Starting a new feature
- Making significant architectural changes
- Working on complex refactoring
- Multiple files/components will be affected
- Requirements are unclear or ambiguous

## How It Works

The planner agent will:

1. **Analyze the request** and restate requirements in clear terms
2. **Break down into phases** following Clean Architecture layers
3. **Identify dependencies** between components
4. **Assess risks** and potential blockers
5. **Estimate complexity** (High/Medium/Low)
6. **Present the plan** and WAIT for your explicit confirmation

## Example Usage

```
User: /plan I need to add a new entity for GitHub Pull Requests

Agent (planner):
# Implementation Plan: GitHub Pull Request Entity

## Requirements Restatement
- Create a new domain entity for GitHub Pull Requests
- Store PR data synced from GitHub API
- Query PRs via GraphQL API
- Follow existing Clean Architecture patterns

## Implementation Phases

### Phase 1: Domain Layer
1. Create value objects in `domain/valueobject/github/`:
   - `GitHubPullRequestId` (value class)
   - `GitHubPullRequestNumber` (value class)
   - `GitHubPullRequestState` (enum: OPEN, CLOSED, MERGED)

2. Create entity in `domain/entity/github/`:
   - `GitHubPullRequest` (data class)

3. Create repository interface in `domain/repository/github/`:
   - `GitHubPullRequestRepository`

4. Create port interface in `domain/port/github/`:
   - `GitHubApiClient`

5. Create error types in `domain/error/`:
   - `GitHubError` sealed class

### Phase 2: Infrastructure Layer
1. Create Flyway migration:
   - `V{version}__create_github_pull_requests_table.sql`

2. Run jOOQ codegen:
   - `./gradlew :infrastructure:jooqCodegen`

3. Create repository implementation:
   - `GitHubPullRequestRepositoryImpl`

4. Create API adapter:
   - `GitHubApiClientImpl` with DTOs

### Phase 3: Application Layer
1. Create use cases:
   - `GitHubPullRequestSyncUseCase` (interface + impl)
   - `GitHubPullRequestFindByIdUseCase` (interface + impl)

2. Create application errors:
   - `GitHubPullRequestSyncError`
   - `GitHubPullRequestFindByIdError`

### Phase 4: Presentation Layer
1. Create GraphQL types:
   - `GitHubPullRequestType`

2. Create GraphQL query:
   - `GitHubPullRequestQuery`

3. Create DataLoader:
   - `GitHubPullRequestDataLoader`

### Phase 5: Framework Layer
1. Create job runner:
   - `GitHubPullRequestSyncRunner`

2. Update configuration:
   - Add beans to `UseCaseConfig`
   - Add beans to `AdapterConfig`

## Dependencies
- GitHub API access token (environment variable)
- PostgreSQL database
- Existing Clean Architecture patterns

## Risks
- HIGH: GitHub API rate limiting
- MEDIUM: Large PR data volume
- LOW: Schema migration on existing data

## Estimated Complexity: MEDIUM

**WAITING FOR CONFIRMATION**: Proceed with this plan? (yes/no/modify)
```

## Important Notes

**CRITICAL**: The planner agent will **NOT** write any code until you explicitly confirm the plan with "yes" or "proceed" or similar affirmative response.

If you want changes, respond with:
- "modify: [your changes]"
- "different approach: [alternative]"
- "skip phase 2 and do phase 3 first"

## Integration with Other Commands

After planning:
- Use `/tdd` to implement with test-driven development
- Use `/build-fix` if build errors occur
- Use `/code-review` to review completed implementation

## Related Agents

This command invokes the `planner` agent.
