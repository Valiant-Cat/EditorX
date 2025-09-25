package editorx.event

/**
 * A minimal, thread-safe event bus.
 * Listeners are stored per event class; publishing walks the class hierarchy
 * so listeners of a supertype also receive subtype events.
 */
interface EventBus {
    fun <T: Any> subscribe(eventType: Class<T>, listener: (T) -> Unit): Subscription
    fun publish(event: Any)
}

interface Subscription { fun unsubscribe() }

class SimpleEventBus : EventBus {
    private val listeners: MutableMap<Class<*>, MutableList<(Any) -> Unit>> = mutableMapOf()

    @Synchronized
    override fun <T: Any> subscribe(eventType: Class<T>, listener: (T) -> Unit): Subscription {
        val list = listeners.getOrPut(eventType) { mutableListOf() }
        val wrapper: (Any) -> Unit = { ev ->
            try {
                @Suppress("UNCHECKED_CAST")
                val typed = ev as T
                listener(typed)
            } catch (_: ClassCastException) {}
        }
        list.add(wrapper)
        return object : Subscription {
            override fun unsubscribe() {
                synchronized(this@SimpleEventBus) {
                    listeners[eventType]?.remove(wrapper)
                    if (listeners[eventType]?.isEmpty() == true) listeners.remove(eventType)
                }
            }
        }
    }

    override fun publish(event: Any) {
        // Snapshot to avoid concurrent modification and reduce lock contention
        val targets: List<(Any) -> Unit> = synchronized(this) {
            val result = mutableListOf<(Any) -> Unit>()
            var type: Class<*>? = event.javaClass
            val visited = HashSet<Class<*>>()
            while (type != null && visited.add(type)) {
                listeners[type]?.let { result.addAll(it) }
                // Also notify listeners registered on interfaces
                type.interfaces.forEach { iface -> listeners[iface]?.let { result.addAll(it) } }
                type = type.superclass
            }
            result.toList()
        }
        targets.forEach { runCatching { it(event) } }
    }
}
