package io.github.barsia.speqa.error

data class SanitizedFrame(
    val module: String,
    val function: String,
    val lineno: Int,
)

data class SanitizedStacktrace(
    val exceptionClass: String,
    val exceptionMessage: String,
    val frames: List<SanitizedFrame>,
)

object SpeqaStacktraceSanitizer {

    fun sanitize(throwable: Throwable): SanitizedStacktrace {
        val frames = throwable.stackTrace.map { element ->
            SanitizedFrame(
                module = element.className,
                function = element.methodName,
                lineno = element.lineNumber.coerceAtLeast(0),
            )
        }
        return SanitizedStacktrace(
            exceptionClass = throwable.javaClass.name,
            exceptionMessage = "",
            frames = frames,
        )
    }
}
