package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.cleanarchitecture.application.error.TransactionError
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueSyncError
import com.wakita181009.cleanarchitecture.application.fixture.JiraFixtures
import com.wakita181009.cleanarchitecture.application.port.TransactionExecutor
import com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.port.jira.JiraApiClient
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraIssueRepository
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraProjectRepository
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraProjectKey
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.OffsetDateTime
import kotlin.test.Test

class JiraIssueSyncUseCaseTest {
    private val jiraProjectRepository = mockk<JiraProjectRepository>()
    private val jiraIssueRepository = mockk<JiraIssueRepository>()
    private val jiraApiClient = mockk<JiraApiClient>()
    private val transactionExecutor = mockk<TransactionExecutor>()

    private val useCase =
        JiraIssueSyncUseCaseImpl(
            jiraProjectRepository = jiraProjectRepository,
            jiraIssueRepository = jiraIssueRepository,
            jiraApiClient = jiraApiClient,
            transactionExecutor = transactionExecutor,
        )

    private fun setupTransactionExecutor() {
        coEvery { transactionExecutor.executeInTransaction<Any>(any()) } coAnswers {
            val block = firstArg<suspend () -> Either<*, Any>>()
            val result = block.invoke()
            result.mapLeft { domainError ->
                TransactionError.ExecutionFailed(
                    message = "Transaction execution failed",
                    cause = domainError as? Throwable,
                )
            }
        }
    }

    // Happy path tests

    @Test
    fun `execute should sync issues and return total count`() =
        runTest {
            setupTransactionExecutor()
            val projectKeys =
                listOf(
                    JiraFixtures
                        .createProjectKey("TEST"),
                )
            val issues =
                JiraFixtures
                    .createJiraIssues(5)

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns projectKeys.right()
            every { jiraApiClient.fetchIssues(projectKeys, any<OffsetDateTime>()) } returns flowOf(issues.right())
            coEvery { jiraIssueRepository.bulkUpsert(issues) } returns issues.right()

            val result = useCase.execute()

            result.shouldBeRight(5)

            coVerify { jiraProjectRepository.findAllProjectKeys() }
            coVerify { jiraIssueRepository.bulkUpsert(issues) }
        }

    @Test
    fun `execute should handle multiple project keys`() =
        runTest {
            setupTransactionExecutor()
            val projectKeys =
                listOf(
                    JiraFixtures
                        .createProjectKey("PROJ1"),
                    JiraFixtures
                        .createProjectKey("PROJ2"),
                    JiraFixtures
                        .createProjectKey("PROJ3"),
                )
            val issues =
                JiraFixtures
                    .createJiraIssues(10)

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns projectKeys.right()
            every { jiraApiClient.fetchIssues(projectKeys, any<OffsetDateTime>()) } returns flowOf(issues.right())
            coEvery { jiraIssueRepository.bulkUpsert(issues) } returns issues.right()

            val result = useCase.execute()

            result.shouldBeRight(10)
        }

    @Test
    fun `execute should accumulate counts across multiple pages`() =
        runTest {
            setupTransactionExecutor()
            val projectKeys =
                listOf(
                    JiraFixtures
                        .createProjectKey("TEST"),
                )
            val page1 =
                JiraFixtures.createJiraIssues(
                    5,
                    keyPrefix = "PAGE1",
                )
            val page2 =
                JiraFixtures.createJiraIssues(
                    3,
                    keyPrefix = "PAGE2",
                )
            val page3 =
                JiraFixtures.createJiraIssues(
                    2,
                    keyPrefix = "PAGE3",
                )

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns projectKeys.right()
            every { jiraApiClient.fetchIssues(projectKeys, any<OffsetDateTime>()) } returns
                flow {
                    emit(page1.right())
                    emit(page2.right())
                    emit(page3.right())
                }
            coEvery { jiraIssueRepository.bulkUpsert(any()) } answers {
                firstArg<List<JiraIssue>>().right()
            }

            val result = useCase.execute()

            result.shouldBeRight(10)

            coVerify(exactly = 3) { jiraIssueRepository.bulkUpsert(any()) }
        }

    // Edge case tests

    @Test
    fun `execute should return zero when no project keys exist`() =
        runTest {
            val emptyProjectKeys = emptyList<JiraProjectKey>()

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns emptyProjectKeys.right()
            every { jiraApiClient.fetchIssues(emptyProjectKeys, any<OffsetDateTime>()) } returns emptyFlow()

            val result = useCase.execute()

            result.shouldBeRight(0)
        }

    @Test
    fun `execute should return zero when API returns empty flow`() =
        runTest {
            val projectKeys =
                listOf(
                    JiraFixtures
                        .createProjectKey("TEST"),
                )

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns projectKeys.right()
            every { jiraApiClient.fetchIssues(projectKeys, any<OffsetDateTime>()) } returns emptyFlow()

            val result = useCase.execute()

            result.shouldBeRight(0)
        }

    @Test
    fun `execute should handle single issue`() =
        runTest {
            setupTransactionExecutor()
            val projectKeys =
                listOf(
                    JiraFixtures
                        .createProjectKey("TEST"),
                )
            val issues =
                listOf(
                    JiraFixtures
                        .createJiraIssue(),
                )

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns projectKeys.right()
            every { jiraApiClient.fetchIssues(projectKeys, any<OffsetDateTime>()) } returns flowOf(issues.right())
            coEvery { jiraIssueRepository.bulkUpsert(issues) } returns issues.right()

            val result = useCase.execute()

            result.shouldBeRight(1)
        }

    // Error handling tests

    @Test
    fun `execute should return ProjectKeyFetchFailed when repository fails`() =
        runTest {
            val dbError =
                JiraError
                    .DatabaseError("Connection failed")

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns dbError.left()

            val result = useCase.execute()

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueSyncError.ProjectKeyFetchFailed>()
                .message
                .shouldBe("Failed to fetch project keys")
        }

    @Test
    fun `execute should return IssueFetchFailed when API returns error`() =
        runTest {
            val projectKeys =
                listOf(
                    JiraFixtures
                        .createProjectKey("TEST"),
                )
            val apiError =
                JiraError
                    .ApiError("API rate limit exceeded")

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns projectKeys.right()
            every { jiraApiClient.fetchIssues(projectKeys, any<OffsetDateTime>()) } returns flowOf(apiError.left())

            val result = useCase.execute()

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueSyncError.IssueFetchFailed>()
                .message
                .shouldBe("Failed to fetch issues")
        }

    @Test
    fun `execute should return IssuePersistFailed when bulkUpsert fails`() =
        runTest {
            setupTransactionExecutor()
            val projectKeys =
                listOf(
                    JiraFixtures
                        .createProjectKey("TEST"),
                )
            val issues =
                JiraFixtures
                    .createJiraIssues(5)
            val dbError =
                JiraError
                    .DatabaseError("Insert failed")

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns projectKeys.right()
            every { jiraApiClient.fetchIssues(projectKeys, any<OffsetDateTime>()) } returns flowOf(issues.right())
            coEvery { jiraIssueRepository.bulkUpsert(issues) } returns dbError.left()

            val result = useCase.execute()

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueSyncError.IssuePersistFailed>()
                .message
                .shouldBe("Failed to persist issues")
        }

    @Test
    fun `execute should fail on first API error in paginated flow`() =
        runTest {
            setupTransactionExecutor()
            val projectKeys =
                listOf(
                    JiraFixtures
                        .createProjectKey("TEST"),
                )
            val page1 =
                JiraFixtures
                    .createJiraIssues(5)
            val apiError =
                JiraError
                    .ApiError("API timeout")

            coEvery { jiraProjectRepository.findAllProjectKeys() } returns projectKeys.right()
            every { jiraApiClient.fetchIssues(projectKeys, any<OffsetDateTime>()) } returns
                flow {
                    emit(page1.right())
                    emit(apiError.left())
                }
            coEvery { jiraIssueRepository.bulkUpsert(any()) } answers {
                firstArg<List<JiraIssue>>().right()
            }

            val result = useCase.execute()

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueSyncError.IssueFetchFailed>()

            coVerify(exactly = 1) { jiraIssueRepository.bulkUpsert(any()) }
        }
}
