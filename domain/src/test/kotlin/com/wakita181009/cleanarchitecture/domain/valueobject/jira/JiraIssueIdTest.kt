package com.wakita181009.cleanarchitecture.domain.valueobject.jira

import com.wakita181009.cleanarchitecture.domain.error.JiraError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class JiraIssueIdTest {
    // Factory invoke tests

    @Test
    fun `invoke should create JiraIssueId with positive Long value`() {
        val id = JiraIssueId(12345L)

        id.value shouldBe 12345L
    }

    @Test
    fun `invoke should create JiraIssueId with zero value`() {
        val id = JiraIssueId(0L)

        id.value shouldBe 0L
    }

    @Test
    fun `invoke should create JiraIssueId with negative value`() {
        val id = JiraIssueId(-1L)

        id.value shouldBe -1L
    }

    @Test
    fun `invoke should create JiraIssueId with max Long value`() {
        val id = JiraIssueId(Long.MAX_VALUE)

        id.value shouldBe Long.MAX_VALUE
    }

    // Factory of() tests - success cases

    @Test
    fun `of should return Right with valid numeric string`() {
        val result = JiraIssueId.of("12345")

        result.shouldBeRight().value shouldBe 12345L
    }

    @Test
    fun `of should return Right with zero string`() {
        val result = JiraIssueId.of("0")

        result.shouldBeRight().value shouldBe 0L
    }

    @Test
    fun `of should return Right with negative string`() {
        val result = JiraIssueId.of("-123")

        result.shouldBeRight().value shouldBe -123L
    }

    @Test
    fun `of should return Right with large number string`() {
        val result = JiraIssueId.of("9223372036854775807")

        result.shouldBeRight().value shouldBe Long.MAX_VALUE
    }

    // Factory of() tests - error cases

    @Test
    fun `of should return Left with InvalidId when string is not a number`() {
        val result = JiraIssueId.of("abc")

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<JiraError.InvalidId>()
    }

    @Test
    fun `of should return Left with InvalidId when string is empty`() {
        val result = JiraIssueId.of("")

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<JiraError.InvalidId>()
    }

    @Test
    fun `of should return Left with InvalidId when string contains spaces`() {
        val result = JiraIssueId.of("123 456")

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<JiraError.InvalidId>()
    }

    @Test
    fun `of should return Left with InvalidId when string contains decimal`() {
        val result = JiraIssueId.of("123.45")

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<JiraError.InvalidId>()
    }

    @Test
    fun `of should return Left with InvalidId when string exceeds Long max value`() {
        val result = JiraIssueId.of("9223372036854775808")

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<JiraError.InvalidId>()
    }

    // Equality tests

    @Test
    fun `two JiraIssueIds with same value should be equal`() {
        val id1 = JiraIssueId(12345L)
        val id2 = JiraIssueId(12345L)

        id1 shouldBe id2
    }

    @Test
    fun `JiraIssueId created via of should equal JiraIssueId created via invoke`() {
        val id1 = JiraIssueId.of("12345").getOrNull()!!
        val id2 = JiraIssueId(12345L)

        id1 shouldBe id2
    }
}
