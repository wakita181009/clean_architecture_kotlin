package com.wakita181009.cleanarchitecture.application.usecase.jira

import arrow.core.left
import arrow.core.right
import com.wakita181009.cleanarchitecture.application.error.jira.JiraIssueListError
import com.wakita181009.cleanarchitecture.application.fixture.JiraFixtures
import com.wakita181009.cleanarchitecture.domain.error.JiraError
import com.wakita181009.cleanarchitecture.domain.repository.jira.JiraIssueRepository
import com.wakita181009.cleanarchitecture.domain.valueobject.Page
import com.wakita181009.cleanarchitecture.domain.valueobject.PageNumber
import com.wakita181009.cleanarchitecture.domain.valueobject.PageSize
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JiraIssueListUseCaseImplTest {
    private val jiraIssueRepository = mockk<JiraIssueRepository>()

    private val useCase = JiraIssueListUseCaseImpl(jiraIssueRepository)

    // Happy path tests

    @Test
    fun `execute should return page of issues with valid pagination`() =
        runTest {
            val issues = JiraFixtures.createJiraIssues(10)
            val expectedPage = Page(totalCount = 100, items = issues)

            coEvery {
                jiraIssueRepository.list(PageNumber(1), PageSize(10))
            } returns expectedPage.right()

            val result = useCase.execute(pageNumber = 1, pageSize = 10)

            result.shouldBeRight().totalCount shouldBe 100
            result.shouldBeRight().items shouldHaveSize 10

            coVerify(exactly = 1) {
                jiraIssueRepository.list(PageNumber(1), PageSize(10))
            }
        }

    @Test
    fun `execute should return empty page when no issues exist`() =
        runTest {
            val expectedPage = Page(totalCount = 0, items = emptyList<com.wakita181009.cleanarchitecture.domain.entity.jira.JiraIssue>())

            coEvery {
                jiraIssueRepository.list(PageNumber(1), PageSize(10))
            } returns expectedPage.right()

            val result = useCase.execute(pageNumber = 1, pageSize = 10)

            result.shouldBeRight().totalCount shouldBe 0
            result.shouldBeRight().items.shouldBeEmpty()
        }

    @Test
    fun `execute should handle different page numbers`() =
        runTest {
            val issues = JiraFixtures.createJiraIssues(5)
            val expectedPage = Page(totalCount = 50, items = issues)

            coEvery {
                jiraIssueRepository.list(PageNumber(5), PageSize(10))
            } returns expectedPage.right()

            val result = useCase.execute(pageNumber = 5, pageSize = 10)

            result.shouldBeRight().totalCount shouldBe 50

            coVerify(exactly = 1) {
                jiraIssueRepository.list(PageNumber(5), PageSize(10))
            }
        }

    @Test
    fun `execute should handle minimum page size`() =
        runTest {
            val issues = listOf(JiraFixtures.createJiraIssue())
            val expectedPage = Page(totalCount = 10, items = issues)

            coEvery {
                jiraIssueRepository.list(PageNumber(1), PageSize(1))
            } returns expectedPage.right()

            val result = useCase.execute(pageNumber = 1, pageSize = 1)

            result.shouldBeRight().items shouldHaveSize 1
        }

    @Test
    fun `execute should handle maximum page size`() =
        runTest {
            val issues = JiraFixtures.createJiraIssues(100)
            val expectedPage = Page(totalCount = 500, items = issues)

            coEvery {
                jiraIssueRepository.list(PageNumber(1), PageSize(100))
            } returns expectedPage.right()

            val result = useCase.execute(pageNumber = 1, pageSize = 100)

            result.shouldBeRight().items shouldHaveSize 100
        }

    // Invalid page number tests

    @Test
    fun `execute should return InvalidPageNumber when page number is zero`() =
        runTest {
            val result = useCase.execute(pageNumber = 0, pageSize = 10)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.InvalidPageNumber>()
                .message shouldContain "Page number must be at least 1"
        }

    @Test
    fun `execute should return InvalidPageNumber when page number is negative`() =
        runTest {
            val result = useCase.execute(pageNumber = -1, pageSize = 10)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.InvalidPageNumber>()
                .message shouldContain "Page number must be at least 1"
        }

    @Test
    fun `execute should return InvalidPageNumber with actual value in message`() =
        runTest {
            val result = useCase.execute(pageNumber = -5, pageSize = 10)

            result
                .shouldBeLeft()
                .message shouldBe "Page number must be at least 1, but was -5"
        }

    @Test
    fun `execute should not call repository when page number is invalid`() =
        runTest {
            useCase.execute(pageNumber = 0, pageSize = 10)

            coVerify(exactly = 0) { jiraIssueRepository.list(any(), any()) }
        }

    // Invalid page size tests - below minimum

    @Test
    fun `execute should return InvalidPageSize when page size is zero`() =
        runTest {
            val result = useCase.execute(pageNumber = 1, pageSize = 0)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.InvalidPageSize>()
                .message shouldContain "Page size must be at least 1"
        }

    @Test
    fun `execute should return InvalidPageSize when page size is negative`() =
        runTest {
            val result = useCase.execute(pageNumber = 1, pageSize = -1)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.InvalidPageSize>()
                .message shouldContain "Page size must be at least 1"
        }

    // Invalid page size tests - above maximum

    @Test
    fun `execute should return InvalidPageSize when page size exceeds maximum`() =
        runTest {
            val result = useCase.execute(pageNumber = 1, pageSize = 101)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.InvalidPageSize>()
                .message shouldContain "Page size must be at most 100"
        }

    @Test
    fun `execute should return InvalidPageSize when page size is much larger than maximum`() =
        runTest {
            val result = useCase.execute(pageNumber = 1, pageSize = 1000)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.InvalidPageSize>()
        }

    @Test
    fun `execute should not call repository when page size is invalid`() =
        runTest {
            useCase.execute(pageNumber = 1, pageSize = 0)

            coVerify(exactly = 0) { jiraIssueRepository.list(any(), any()) }
        }

    // Repository error tests

    @Test
    fun `execute should return IssueFetchFailed when repository returns DatabaseError`() =
        runTest {
            val dbError = JiraError.DatabaseError("Connection failed")

            coEvery {
                jiraIssueRepository.list(PageNumber(1), PageSize(10))
            } returns dbError.left()

            val result = useCase.execute(pageNumber = 1, pageSize = 10)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.IssueFetchFailed>()
                .message shouldBe "Failed to fetch JIRA issues"
        }

    @Test
    fun `execute should return IssueFetchFailed when repository returns ApiError`() =
        runTest {
            val apiError = JiraError.ApiError("API unavailable")

            coEvery {
                jiraIssueRepository.list(PageNumber(1), PageSize(10))
            } returns apiError.left()

            val result = useCase.execute(pageNumber = 1, pageSize = 10)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.IssueFetchFailed>()
        }

    @Test
    fun `execute should wrap JiraError cause in IssueFetchFailed`() =
        runTest {
            val originalException = RuntimeException("Underlying issue")
            val dbError = JiraError.DatabaseError("Query failed", originalException)

            coEvery {
                jiraIssueRepository.list(PageNumber(1), PageSize(10))
            } returns dbError.left()

            val result = useCase.execute(pageNumber = 1, pageSize = 10)

            val error = result.shouldBeLeft().shouldBeInstanceOf<JiraIssueListError.IssueFetchFailed>()
            error.cause.shouldBeInstanceOf<JiraError.DatabaseError>()
        }

    // Validation order tests

    @Test
    fun `execute should validate page number before page size`() =
        runTest {
            val result = useCase.execute(pageNumber = 0, pageSize = 0)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.InvalidPageNumber>()
        }

    @Test
    fun `execute should validate page size when page number is valid`() =
        runTest {
            val result = useCase.execute(pageNumber = 1, pageSize = 0)

            result
                .shouldBeLeft()
                .shouldBeInstanceOf<JiraIssueListError.InvalidPageSize>()
        }
}
