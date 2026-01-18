package com.wakita181009.cleanarchitecture.infrastructure.adapter.jira

import arrow.core.Either
import arrow.core.filterOption
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.port.jira.JiraApiClient
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectKey
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.kotlin.retry.decorateSuspendFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class JiraApiClientImpl(
    private val okHttpClient: OkHttpClient,
    @param:Qualifier("jiraApiToken") private val jiraApiToken: String,
) : JiraApiClient {
    companion object {
        private const val MAX_RESULTS = 100
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_INTERVAL = 500L
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val API_CALL_DELAY_MS = 1000L
        private val jsonMapper =
            jacksonObjectMapper()
                .apply {
                    registerModule(JavaTimeModule())
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
        private val retryConfig =
            RetryConfig
                .custom<Any>()
                .maxAttempts(MAX_ATTEMPTS)
                .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(INITIAL_BACKOFF_INTERVAL, BACKOFF_MULTIPLIER),
                ).build()

        private val ISSUE_FIELDS =
            listOf(
                "project",
                "summary",
                "description",
                "issuetype",
                "priority",
                "created",
                "updated",
            )
    }

    override fun fetchIssues(
        projectKeys: List<JiraProjectKey>,
        since: OffsetDateTime,
    ): Flow<Either<JiraError, List<JiraIssue>>> =
        flow {
            val sinceDate = since.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val jql = "project in (${projectKeys.joinToString(", ") { it.value }}) AND created >= '$sinceDate'"
            var nextPageToken: String? = null
            do {
                val result = fetchPage(jql, nextPageToken)
                val isLast =
                    result
                        .onLeft { emit(it.left()) }
                        .onRight { response ->
                            emit(
                                response.issues
                                    .map { it.toDomain() }
                                    .filterOption()
                                    .right(),
                            )
                            nextPageToken = response.nextPageToken
                        }.fold({ true }, { it.isLast })
                if (isLast) break
                delay(API_CALL_DELAY_MS)
            } while (true)
        }.flowOn(Dispatchers.IO)

    private suspend fun fetchPage(
        jql: String,
        nextPageToken: String?,
    ): Either<JiraError, JiraSearchResponse> =
        Either
            .catch {
                Retry
                    .of("fetchIssues", retryConfig)
                    .decorateSuspendFunction {
                        val request =
                            JiraSearchRequest(
                                jql = jql,
                                fields = ISSUE_FIELDS,
                                maxResults = MAX_RESULTS,
                                nextPageToken = nextPageToken,
                            )
                        val requestBody = jsonMapper.writeValueAsString(request).toRequestBody("application/json".toMediaType())

                        okHttpClient
                            .newCall(
                                Request
                                    .Builder()
                                    .url("https://anymindgroup.atlassian.net/rest/api/3/search/jql")
                                    .addHeader("Authorization", "Basic $jiraApiToken")
                                    .post(requestBody)
                                    .build(),
                            ).execute()
                            .use { res ->
                                jsonMapper.readValue<JiraSearchResponse>(res.body.string())
                            }
                    }.invoke()
            }.mapLeft { e ->
                JiraError.ApiError(
                    message = "Failed to fetch issues from Jira API: ${e.message}",
                    cause = e,
                )
            }
}
