package editorx.command

/**
 * Command framework for EditorX. Commands are identified by a stable id and
 * carry human-readable title/description for palette/menus.
 */
data class CommandMeta(
    val id: String,
    val title: String,
    val description: String? = null
)

class CommandContext(
    val args: Map<String, Any?> = emptyMap()
)

typealias CommandHandler = (CommandContext) -> Unit

interface CommandRegistry {
    fun register(meta: CommandMeta, handler: CommandHandler)
    fun unregister(id: String)
    fun execute(id: String, args: Map<String, Any?> = emptyMap()): Boolean
    fun all(): List<CommandMeta>
    fun has(id: String): Boolean
}

class DefaultCommandRegistry : CommandRegistry {
    private val handlers = linkedMapOf<String, Pair<CommandMeta, CommandHandler>>()

    @Synchronized
    override fun register(meta: CommandMeta, handler: CommandHandler) {
        handlers[meta.id] = meta to handler
    }

    @Synchronized
    override fun unregister(id: String) {
        handlers.remove(id)
    }

    override fun execute(id: String, args: Map<String, Any?>): Boolean {
        val entry = synchronized(this) { handlers[id] }
        val handler = entry?.second ?: return false
        handler(CommandContext(args))
        return true
    }

    override fun all(): List<CommandMeta> = synchronized(this) { handlers.values.map { it.first } }

    override fun has(id: String): Boolean = synchronized(this) { handlers.containsKey(id) }
}

