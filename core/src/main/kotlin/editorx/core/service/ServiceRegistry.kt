package editorx.core.service

/**
 * 核心服务注册表
 */
interface ServiceRegistry {

    /**
     * 获取所有注册的服务实例（用于支持多实例的场景）
     */
    fun <T : Any> getAll(serviceClass: Class<T>): List<T>
}

class MutableServiceRegistry : ServiceRegistry {
    private val multiServices: MutableMap<Class<*>, MutableList<Any>> = mutableMapOf()

    /**
     * 注册服务（多实例支持）
     */
    fun <T : Any> registerMulti(serviceClass: Class<T>, instance: T) {
        multiServices.getOrPut(serviceClass) { mutableListOf() }.add(instance)
    }

    /**
     * 取消注册服务（多实例）
     */
    fun <T : Any> unregisterMulti(serviceClass: Class<T>, instance: T) {
        multiServices[serviceClass]?.remove(instance)
    }

    override fun <T : Any> getAll(serviceClass: Class<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return multiServices[serviceClass]?.map { it as T } ?: emptyList()
    }
}
