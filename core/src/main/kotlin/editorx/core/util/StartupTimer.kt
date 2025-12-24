package editorx.core.util

import org.slf4j.Logger

class StartupTimer(private val name: String) {
    private val startNanos = System.nanoTime()
    private val events: MutableList<Pair<String, Long>> = mutableListOf()

    fun mark(event: String) {
        events += event to elapsedMillis()
    }

    fun dump(logger: Logger) {
        val total = elapsedMillis()
        val builder = StringBuilder()
        builder.append("StartupTimer[").append(name).append("] total=").append(total).append("ms; ")
        events.forEachIndexed { index, (event, time) ->
            builder.append(event).append('=').append(time).append("ms")
            if (index < events.lastIndex) builder.append(", ")
        }
        logger.info(builder.toString())
    }

    private fun elapsedMillis(): Long = (System.nanoTime() - startNanos) / 1_000_000
}
