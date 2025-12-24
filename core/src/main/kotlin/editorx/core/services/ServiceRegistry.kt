package editorx.core.services

/**
 * 核心服务注册表，插件通过 [editorx.core.plugin.PluginContext.services] 访问。
 */
interface ServiceRegistry {
    fun <T : Any> get(serviceClass: Class<T>): T?

    fun <T : Any> require(serviceClass: Class<T>): T =
        get(serviceClass)
            ?: error("Service not found: ${serviceClass.name}")
}

class MutableServiceRegistry : ServiceRegistry {
    private val services: MutableMap<Class<*>, Any> = linkedMapOf()

    fun <T : Any> register(serviceClass: Class<T>, instance: T) {
        services[serviceClass] = instance
    }

    fun <T : Any> unregister(serviceClass: Class<T>) {
        services.remove(serviceClass)
    }

    override fun <T : Any> get(serviceClass: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return services[serviceClass] as? T
    }
}
