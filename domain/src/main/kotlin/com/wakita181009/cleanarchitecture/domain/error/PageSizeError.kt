package com.wakita181009.cleanarchitecture.domain.error

import com.wakita181009.cleanarchitecture.domain.valueobject.PageSize

sealed class PageSizeError(
    val message: String,
) {
    data class BelowMinimum(
        val value: Int,
    ) : PageSizeError("Page size must be at least ${PageSize.MIN_VALUE}, but was $value")

    data class AboveMaximum(
        val value: Int,
    ) : PageSizeError("Page size must be at most ${PageSize.MAX_VALUE}, but was $value")
}
