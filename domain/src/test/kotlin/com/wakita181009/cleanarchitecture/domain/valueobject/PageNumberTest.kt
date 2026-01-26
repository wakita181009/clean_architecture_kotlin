package com.wakita181009.cleanarchitecture.domain.valueobject

import com.wakita181009.cleanarchitecture.domain.error.PageNumberError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class PageNumberTest {
    // Factory invoke tests

    @Test
    fun `invoke should create PageNumber with valid value`() {
        val pageNumber = PageNumber(1)

        pageNumber.value shouldBe 1
    }

    @Test
    fun `invoke should create PageNumber with large value`() {
        val pageNumber = PageNumber(1000)

        pageNumber.value shouldBe 1000
    }

    // Factory of() tests - success cases

    @Test
    fun `of should return Right with minimum valid value`() {
        val result = PageNumber.of(PageNumber.MIN_VALUE)

        result.shouldBeRight().value shouldBe 1
    }

    @Test
    fun `of should return Right with value above minimum`() {
        val result = PageNumber.of(5)

        result.shouldBeRight().value shouldBe 5
    }

    @Test
    fun `of should return Right with large value`() {
        val result = PageNumber.of(1000)

        result.shouldBeRight().value shouldBe 1000
    }

    @Test
    fun `of should return Right with max int value`() {
        val result = PageNumber.of(Int.MAX_VALUE)

        result.shouldBeRight().value shouldBe Int.MAX_VALUE
    }

    // Factory of() tests - error cases

    @Test
    fun `of should return Left with BelowMinimum when value is zero`() {
        val result = PageNumber.of(0)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageNumberError.BelowMinimum>()
            .value shouldBe 0
    }

    @Test
    fun `of should return Left with BelowMinimum when value is negative`() {
        val result = PageNumber.of(-1)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageNumberError.BelowMinimum>()
            .value shouldBe -1
    }

    @Test
    fun `of should return Left with BelowMinimum when value is large negative`() {
        val result = PageNumber.of(-100)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageNumberError.BelowMinimum>()
            .value shouldBe -100
    }

    // Error message tests

    @Test
    fun `BelowMinimum error should have correct message`() {
        val result = PageNumber.of(0)

        result
            .shouldBeLeft()
            .message shouldBe "Page number must be at least 1, but was 0"
    }

    @Test
    fun `BelowMinimum error should include actual value in message`() {
        val result = PageNumber.of(-5)

        result
            .shouldBeLeft()
            .message shouldBe "Page number must be at least 1, but was -5"
    }

    // Equality tests

    @Test
    fun `two PageNumbers with same value should be equal`() {
        val page1 = PageNumber(5)
        val page2 = PageNumber(5)

        page1 shouldBe page2
    }

    @Test
    fun `PageNumber created via of should equal PageNumber created via invoke`() {
        val page1 = PageNumber.of(5).getOrNull()!!
        val page2 = PageNumber(5)

        page1 shouldBe page2
    }

    // MIN_VALUE constant test

    @Test
    fun `MIN_VALUE should be 1`() {
        PageNumber.MIN_VALUE shouldBe 1
    }
}
