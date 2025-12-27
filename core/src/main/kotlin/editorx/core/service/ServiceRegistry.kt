package editorx.core.service

/**
 * 核心服务注册表
 */
interface ServiceRegistry {

    /**
     * 获取所有注册的服务实例（用于支持多实例的场景）
     */
    fun <T : Any> getService(serviceClass: Class<T>): List<T>

    /**
     * 注册服务（多实例支持）
     */
    fun <T : Any> registerService(serviceClass: Class<T>, instance: T)

    /**
     * 取消注册服务（多实例）
     */
    fun <T : Any> unregisterService(serviceClass: Class<T>, instance: T)
}

class MutableServiceRegistry : ServiceRegistry {
    private val multiServices: MutableMap<Class<*>, MutableList<Any>> = mutableMapOf()

    override fun <T : Any> getService(serviceClass: Class<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return multiServices[serviceClass]?.map { it as T } ?: emptyList()
    }

    override fun <T : Any> registerService(serviceClass: Class<T>, instance: T) {
        multiServices.getOrPut(serviceClass) { mutableListOf() }.add(instance)
    }

    override fun <T : Any> unregisterService(serviceClass: Class<T>, instance: T) {
        multiServices[serviceClass]?.remove(instance)
    }
}
