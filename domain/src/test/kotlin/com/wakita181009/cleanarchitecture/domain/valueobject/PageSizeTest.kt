package com.wakita181009.cleanarchitecture.domain.valueobject

import com.wakita181009.cleanarchitecture.domain.error.PageSizeError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class PageSizeTest {
    // Factory invoke tests

    @Test
    fun `invoke should create PageSize with valid value`() {
        val pageSize = PageSize(50)

        pageSize.value shouldBe 50
    }

    @Test
    fun `invoke should create PageSize with minimum value`() {
        val pageSize = PageSize(1)

        pageSize.value shouldBe 1
    }

    @Test
    fun `invoke should create PageSize with maximum value`() {
        val pageSize = PageSize(100)

        pageSize.value shouldBe 100
    }

    // Factory of() tests - success cases

    @Test
    fun `of should return Right with minimum valid value`() {
        val result = PageSize.of(PageSize.MIN_VALUE)

        result.shouldBeRight().value shouldBe 1
    }

    @Test
    fun `of should return Right with maximum valid value`() {
        val result = PageSize.of(PageSize.MAX_VALUE)

        result.shouldBeRight().value shouldBe 100
    }

    @Test
    fun `of should return Right with value in middle of range`() {
        val result = PageSize.of(50)

        result.shouldBeRight().value shouldBe 50
    }

    @Test
    fun `of should return Right with value just above minimum`() {
        val result = PageSize.of(2)

        result.shouldBeRight().value shouldBe 2
    }

    @Test
    fun `of should return Right with value just below maximum`() {
        val result = PageSize.of(99)

        result.shouldBeRight().value shouldBe 99
    }

    // Factory of() tests - below minimum error cases

    @Test
    fun `of should return Left with BelowMinimum when value is zero`() {
        val result = PageSize.of(0)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageSizeError.BelowMinimum>()
            .value shouldBe 0
    }

    @Test
    fun `of should return Left with BelowMinimum when value is negative`() {
        val result = PageSize.of(-1)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageSizeError.BelowMinimum>()
            .value shouldBe -1
    }

    @Test
    fun `of should return Left with BelowMinimum when value is large negative`() {
        val result = PageSize.of(-100)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageSizeError.BelowMinimum>()
            .value shouldBe -100
    }

    // Factory of() tests - above maximum error cases

    @Test
    fun `of should return Left with AboveMaximum when value exceeds maximum`() {
        val result = PageSize.of(101)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageSizeError.AboveMaximum>()
            .value shouldBe 101
    }

    @Test
    fun `of should return Left with AboveMaximum when value is much larger than maximum`() {
        val result = PageSize.of(1000)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageSizeError.AboveMaximum>()
            .value shouldBe 1000
    }

    @Test
    fun `of should return Left with AboveMaximum when value is max int`() {
        val result = PageSize.of(Int.MAX_VALUE)

        result
            .shouldBeLeft()
            .shouldBeInstanceOf<PageSizeError.AboveMaximum>()
            .value shouldBe Int.MAX_VALUE
    }

    // Error message tests

    @Test
    fun `BelowMinimum error should have correct message`() {
        val result = PageSize.of(0)

        result
            .shouldBeLeft()
            .message shouldBe "Page size must be at least 1, but was 0"
    }

    @Test
    fun `AboveMaximum error should have correct message`() {
        val result = PageSize.of(150)

        result
            .shouldBeLeft()
            .message shouldBe "Page size must be at most 100, but was 150"
    }

    // Equality tests

    @Test
    fun `two PageSizes with same value should be equal`() {
        val size1 = PageSize(50)
        val size2 = PageSize(50)

        size1 shouldBe size2
    }

    @Test
    fun `PageSize created via of should equal PageSize created via invoke`() {
        val size1 = PageSize.of(50).getOrNull()!!
        val size2 = PageSize(50)

        size1 shouldBe size2
    }

    // Constants tests

    @Test
    fun `MIN_VALUE should be 1`() {
        PageSize.MIN_VALUE shouldBe 1
    }

    @Test
    fun `MAX_VALUE should be 100`() {
        PageSize.MAX_VALUE shouldBe 100
    }
}
