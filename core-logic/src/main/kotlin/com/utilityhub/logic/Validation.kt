package com.utilityhub.logic

/** Result of validating user input. Pure and platform independent. */
sealed interface Validation<out T> {
    data class Valid<T>(val value: T) : Validation<T>
    data class Invalid(val message: String) : Validation<Nothing>
}

inline fun <T, R> Validation<T>.map(transform: (T) -> R): Validation<R> = when (this) {
    is Validation.Valid -> Validation.Valid(transform(value))
    is Validation.Invalid -> this
}

val <T> Validation<T>.valueOrNull: T? get() = (this as? Validation.Valid)?.value

/** Outcome of a tool operation. Distinguishes every case the product rules care about. */
sealed interface ProcessingResult<out T> {
    data class Success<T>(val value: T) : ProcessingResult<T>
    data object Cancelled : ProcessingResult<Nothing>
    data class ValidationFailure(val message: String) : ProcessingResult<Nothing>
    data class Unsupported(val message: String) : ProcessingResult<Nothing>
    data class Failure(val message: String, val cause: Throwable? = null) : ProcessingResult<Nothing>
}
