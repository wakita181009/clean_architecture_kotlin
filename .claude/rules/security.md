# Security Guidelines

## Mandatory Security Checks

Before ANY commit:
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] All user inputs validated
- [ ] SQL injection prevention (parameterized queries via jOOQ)
- [ ] GraphQL query depth limiting configured
- [ ] Authentication/authorization verified
- [ ] Rate limiting on all endpoints
- [ ] Error messages don't leak sensitive data

## Secret Management

```kotlin
// NEVER: Hardcoded secrets
val apiToken = "your-api-token-here"  // BAD

// ALWAYS: Environment variables via Spring configuration
@ConfigurationProperties(prefix = "jira")
data class JiraProperties(
    val apiToken: String,
    val baseUrl: String,
)

// Usage
@Component
class JiraIssueAdapterImpl(
    private val properties: JiraProperties,
) : JiraIssuePort {
    // properties.apiToken is loaded from environment
}
```

## Environment Variable Loading

```kotlin
// application.yml
jira:
  api-token: ${JIRA_API_TOKEN}
  base-url: ${JIRA_BASE_URL:https://your-domain.atlassian.net}

// Fail fast if required variable is missing
@PostConstruct
fun validateProperties() {
    require(properties.apiToken.isNotBlank()) {
        "JIRA_API_TOKEN environment variable is required"
    }
}
```

## SQL Injection Prevention

jOOQ provides built-in protection with parameterized queries:

```kotlin
// GOOD: jOOQ parameterized query (safe)
dsl.selectFrom(JIRA_ISSUES)
    .where(JIRA_ISSUES.KEY.eq(issueKey.value))
    .awaitFirstOrNull()

// BAD: Raw SQL with string concatenation (vulnerable)
dsl.execute("SELECT * FROM jira_issues WHERE key = '${issueKey.value}'")  // NEVER
```

## GraphQL Security

```kotlin
// Configure query depth limit
@Bean
fun schemaDirectiveWiring(): SchemaDirectiveWiring {
    return MaxQueryDepthInstrumentation(10)  // Prevent deeply nested queries
}

// Configure query complexity limit
@Bean
fun complexityAnalysis(): Instrumentation {
    return MaxQueryComplexityInstrumentation(100)
}
```

## Logging Security

```kotlin
// NEVER log sensitive data
logger.info("Processing request for user: ${user.email}")  // BAD if PII

// GOOD: Log identifiers, not sensitive data
logger.info("Processing request for user ID: ${user.id}")

// GOOD: Mask sensitive data if needed
logger.info("API call with token: ${apiToken.take(4)}****")
```

## Security Response Protocol

If security issue found:
1. STOP immediately
2. Use **security-reviewer** agent
3. Fix CRITICAL issues before continuing
4. Rotate any exposed secrets
5. Review entire codebase for similar issues