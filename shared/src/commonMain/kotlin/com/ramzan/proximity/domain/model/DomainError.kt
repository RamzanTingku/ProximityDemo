package com.ramzan.proximity.domain.model

sealed interface DomainError {
    data class PermissionMissing(val capability: Capability) : DomainError
    data class ServiceDisabled(val capability: Capability) : DomainError
    data class Unsupported(val capability: Capability) : DomainError
    data class ScanFailed(val code: Int) : DomainError
    data class ForegroundStartFailed(val reason: String?) : DomainError
    data class InvalidCode(val reason: String) : DomainError
    data class Unavailable(val capability: Capability) : DomainError
    data class SessionFailed(val reason: String?) : DomainError
    data object NoActiveOffer : DomainError
    data object Timeout : DomainError
    data class Tag(val problem: TagProblem) : DomainError
    data class Unknown(val message: String?) : DomainError
}

enum class TagProblem {
    NOT_NDEF,
    READ_ONLY,
    TOO_SMALL,
    LOST,
    IO_FAILURE,
    FORMAT_FAILED,
}

/** Carries a [DomainError] across callback and Flow boundaries that can only throw. */
class DomainException(val error: DomainError) : Exception(error.toString())

sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>
    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
}

inline fun <T, R> DomainResult<T>.flatMap(transform: (T) -> DomainResult<R>): DomainResult<R> = when (this) {
    is DomainResult.Success -> transform(value)
    is DomainResult.Failure -> this
}

fun <T> DomainResult<T>.errorOrNull(): DomainError? = (this as? DomainResult.Failure)?.error

fun <T> DomainResult<T>.getOrNull(): T? = (this as? DomainResult.Success)?.value

/** Maps whatever a Flow or callback threw into the domain vocabulary. */
fun Throwable.toDomainError(): DomainError = when (this) {
    is DomainException -> error
    else -> DomainError.Unknown(message ?: this::class.simpleName)
}
