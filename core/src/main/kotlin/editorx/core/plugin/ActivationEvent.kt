package editorx.core.plugin

/**
 * 插件激活事件定义。
 * 
 * - `OnStartup`: 主窗口就绪后自动激活；
 * - `OnCommand`: 命令触发时激活；
 * - `OnDemand`: 仅手动启用；
 */
sealed class ActivationEvent {
    object OnStartup : ActivationEvent() {
        override fun toString(): String = "onStartup"
    }

    data class OnCommand(val commandId: String) : ActivationEvent() {
        override fun toString(): String = "onCommand:$commandId"
    }

    object OnDemand : ActivationEvent() {
        override fun toString(): String = "onDemand"
    }

    companion object {
        fun parse(raw: String): ActivationEvent? {
            val normalized = raw.trim()
            if (normalized.isEmpty()) return null
            return when {
                normalized.equals("onStartup", ignoreCase = true) -> OnStartup
                normalized.equals("onDemand", ignoreCase = true) -> OnDemand
                normalized.startsWith("onCommand:", ignoreCase = true) -> {
                    val command = normalized.substringAfter(":").trim()
                    if (command.isEmpty()) null else OnCommand(command)
                }
                else -> null
            }
        }
    }
}
