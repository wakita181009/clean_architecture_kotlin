package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.left
import arrow.core.right
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueFindByIdError
import com.wakita181009.cleanarchitecture.application.fixture.JiraFixtures
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraIssueRepository
import com.wakita181009.cleanarchitecture.domain.valueobject.jira.JiraIssueId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JiraIssueFindByIdsUseCaseImplTest {
    private val jiraIssueRepository = mockk<JiraIssueRepository>()

    private val useCase = JiraIssueFindByIdsUseCaseImpl(jiraIssueRepository)

    // Happy path tests

    @Test
    fun `execute should return issues when repository returns successfully`() =
        runTest {
            val ids = listOf(JiraIssueId(1L), JiraIssueId(2L), JiraIssueId(3L))
            val expectedIssues =
                listOf(
                    JiraFixtures.createJiraIssue(id = 1L, key = "TEST-1"),
                    JiraFixtures.createJiraIssue(id = 2L, key = "TEST-2"),
                    JiraFixtures.createJiraIssue(id = 3L, key = "TEST-3"),
                )

            coEvery { jiraIssueRepository.findByIds(ids) } returns expectedIssues.right()

            val result = useCase.execute(ids)

            result.shouldBeRight() shouldHaveSize 3
            result.shouldBeRight().map { it.id } shouldContainExactly ids

            coVerify(exactly = 1) { jiraIssueRepository.findByIds(ids) }
        }

    @Test
    fun `execute should return single issue when requesting one id`() =
        runTest {
            val ids = listOf(JiraIssueId(12345L))
            val expectedIssue = JiraFixtures.createJiraIssue(id = 12345L, key = "TEST-123")

            coEvery { jiraIssueRepository.findByIds(ids) } returns listOf(expectedIssue).right()

            val result = useCase.execute(ids)

            result.shouldBeRight() shouldHaveSize 1
            result.shouldBeRight().first().id shouldBe JiraIssueId(12345L)
        }

    @Test
    fun `execute should return empty list when requesting empty ids`() =
        runTest {
            val ids = emptyList<JiraIssueId>()

            coEvery { jiraIssueRepository.findByIds(ids) } returns
                emptyList<com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue>().right()

            val result = useCase.execute(ids)

            result.shouldBeRight().shouldBeEmpty()

            coVerify(exactly = 1) { jiraIssueRepository.findByIds(ids) }
        }

    @Test
    fun `execute should return partial results when some ids are not found`() =
        runTest {
            val ids = listOf(JiraIssueId(1L), JiraIssueId(2L), JiraIssueId(3L))
            val partialIssues =
                listOf(
                    JiraFixtures.createJiraIssue(id = 1L, key = "TEST-1"),
                    JiraFixtures.createJiraIssue(id = 3L, key = "TEST-3"),
                )

            coEvery { jiraIssueRepository.findByIds(ids) } returns partialIssues.right()

            val result = useCase.execute(ids)

            result.shouldBeRight() shouldHaveSize 2
        }

    @Test
    fun `execute should handle large number of ids`() =
        runTest {
            val ids = (1L..100L).map { JiraIssueId(it) }
            val issues =
                (1L..100L).map { id ->
                    JiraFixtures.createJiraIssue(id = id, key = "TEST-$id")
                }

            coEvery { jiraIssueRepository.findByIds(ids) } returns issues.right()

            val result = useCase.execute(ids)

            result.shouldBeRight() shouldHaveSize 100
        }

    // Error handling tests

    @Test
    fun `execute should return IssueFetchFailed when repository returns DatabaseError`() =
        runTest {
            val ids = listOf(JiraIssueId(1L))
            val dbError = JiraError.DatabaseError("Connection timeout")

            coEvery { jiraIssueRepository.findByIds(ids) } returns dbError.left()

            val result = useCase.execute(ids)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueFindByIdError.IssueFetchFailed>()
                .message shouldBe "Failed to fetch JIRA issues"
        }

    @Test
    fun `execute should return IssueFetchFailed when repository returns ApiError`() =
        runTest {
            val ids = listOf(JiraIssueId(1L))
            val apiError = JiraError.ApiError("API rate limit exceeded")

            coEvery { jiraIssueRepository.findByIds(ids) } returns apiError.left()

            val result = useCase.execute(ids)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueFindByIdError.IssueFetchFailed>()
        }

    @Test
    fun `execute should return IssueFetchFailed when repository returns InvalidId`() =
        runTest {
            val ids = listOf(JiraIssueId(1L))
            val invalidIdError = JiraError.InvalidId(IllegalArgumentException("Invalid ID format"))

            coEvery { jiraIssueRepository.findByIds(ids) } returns invalidIdError.left()

            val result = useCase.execute(ids)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueFindByIdError.IssueFetchFailed>()
        }

    @Test
    fun `execute should wrap JiraError cause in IssueFetchFailed`() =
        runTest {
            val ids = listOf(JiraIssueId(1L))
            val originalException = RuntimeException("Underlying cause")
            val dbError = JiraError.DatabaseError("Database failed", originalException)

            coEvery { jiraIssueRepository.findByIds(ids) } returns dbError.left()

            val result = useCase.execute(ids)

            val error = result.shouldBeLeft().shouldBeInstanceOf<JiraIssueFindByIdError.IssueFetchFailed>()
            error.cause.shouldBeInstanceOf<JiraError.DatabaseError>()
        }

    // Edge cases

    @Test
    fun `execute should handle duplicate ids in input`() =
        runTest {
            val ids = listOf(JiraIssueId(1L), JiraIssueId(1L), JiraIssueId(2L))
            val issues =
                listOf(
                    JiraFixtures.createJiraIssue(id = 1L, key = "TEST-1"),
                    JiraFixtures.createJiraIssue(id = 2L, key = "TEST-2"),
                )

            coEvery { jiraIssueRepository.findByIds(ids) } returns issues.right()

            val result = useCase.execute(ids)

            result.shouldBeRight()

            coVerify(exactly = 1) { jiraIssueRepository.findByIds(ids) }
        }
}
