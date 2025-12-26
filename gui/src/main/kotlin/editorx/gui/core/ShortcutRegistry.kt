package editorx.gui.core

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * 统一的快捷键注册表（单例）
 * 负责管理全局快捷键快捷键的注册与注销
 */
object ShortcutRegistry {

    /**
     * 快捷键绑定信息
     */
    data class ShortcutBinding(
        val id: String,
        val keyStroke: KeyStroke,
        val descriptionKey: String, // i18n 翻译 key
        val dispatcher: KeyEventDispatcher
    ) {
        /**
         * 获取已翻译的描述文本
         */
        val displayDescription: String
            get() = I18n.translate(descriptionKey)
    }

    private val globalBindings = mutableMapOf<String, ShortcutBinding>()

    private val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()

    /**
     * 设置默认快捷键
     *
     * @param onGlobalSearch 全局搜索回调
     * @param onOpenSettings 打开设置回调
     */
    fun setupDefaultShortcuts(
        onGlobalSearch: () -> Unit,
        onOpenSettings: () -> Unit
    ) {
        // 双击 Shift - 全局搜索
        registerDoubleShortcut(
            id = "global.search",
            keyCode = KeyEvent.VK_SHIFT,
            descriptionKey = I18nKeys.Shortcut.GLOBAL_SEARCH,
            action = onGlobalSearch
        )

        // Command+, - 打开设置
        val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        registerCustomShortcut(
            id = "global.settings",
            keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, shortcutMask),
            descriptionKey = I18nKeys.Shortcut.OPEN_SETTINGS
        ) {
            onOpenSettings()
        }
    }

    // -----------------------------------------------------------------------------

    /**
     * 注册全局快捷键（使用 KeyboardFocusManager）
     * 适用于需要在任何焦点状态下都能触发的快捷键
     *
     * @param descriptionKey i18n 翻译 key，用于获取描述文本
     */
    fun registerCustomShortcut(
        id: String,
        keyStroke: KeyStroke,
        descriptionKey: String,
        action: () -> Unit
    ) {
        // 检查是否已注册
        if (globalBindings.containsKey(id)) {
            unregisterShortcut(id)
        }

        // 获取扩展修饰键掩码（KeyStroke.modifiers 是旧掩码，需要转换）
        // KeyStroke.getKeyStroke() 创建的 KeyStroke 使用旧掩码，但我们需要扩展掩码来匹配 KeyEvent.modifiersEx
        val expectedModifiers = keyStroke.modifiers
        // 将旧掩码转换为扩展掩码
        val expectedModifiersEx = convertToExtendedModifiers(expectedModifiers)

        val dispatcher = KeyEventDispatcher { e: java.awt.event.KeyEvent ->
            // 如果有模态对话框打开，不处理全局快捷键，让对话框自己处理
            val activeWindow = focusManager.activeWindow
            if (activeWindow is javax.swing.JDialog && activeWindow.isModal) {
                return@KeyEventDispatcher false
            }

            if (e.id == KeyEvent.KEY_PRESSED &&
                !e.isConsumed &&
                keyStroke.keyCode == e.keyCode
            ) {
                val actualModifiers = e.modifiersEx

                // 检查期望的修饰键是否都被按下，且没有其他修饰键
                if ((actualModifiers and expectedModifiersEx) == expectedModifiersEx &&
                    (actualModifiers and expectedModifiersEx.inv()) == 0
                ) {
                    javax.swing.SwingUtilities.invokeLater { action() }
                    true // 消费事件
                } else {
                    false
                }
            } else {
                false
            }
        }

        focusManager.addKeyEventDispatcher(dispatcher)
        globalBindings[id] = ShortcutBinding(id, keyStroke, descriptionKey, dispatcher)
    }

    /**
     * 将旧的修饰键掩码转换为扩展修饰键掩码
     */
    private fun convertToExtendedModifiers(oldModifiers: Int): Int {
        var extended = 0
        if ((oldModifiers and java.awt.event.InputEvent.SHIFT_MASK) != 0) {
            extended = extended or java.awt.event.InputEvent.SHIFT_DOWN_MASK
        }
        if ((oldModifiers and java.awt.event.InputEvent.CTRL_MASK) != 0) {
            extended = extended or java.awt.event.InputEvent.CTRL_DOWN_MASK
        }
        if ((oldModifiers and java.awt.event.InputEvent.ALT_MASK) != 0) {
            extended = extended or java.awt.event.InputEvent.ALT_DOWN_MASK
        }
        if ((oldModifiers and java.awt.event.InputEvent.META_MASK) != 0) {
            extended = extended or java.awt.event.InputEvent.META_DOWN_MASK
        }
        return extended
    }

    /**
     * 注册特殊的全局快捷键（如双击 Shift）
     * 使用自定义的 KeyEventDispatcher 逻辑
     *
     * @param descriptionKey i18n 翻译 key，用于获取描述文本
     */
    fun registerCustomShortcut(
        id: String,
        keyStroke: KeyStroke,
        descriptionKey: String,
        dispatcher: KeyEventDispatcher
    ) {
        // 检查是否已注册
        if (globalBindings.containsKey(id)) {
            unregisterShortcut(id)
        }

        focusManager.addKeyEventDispatcher(dispatcher)
        globalBindings[id] = ShortcutBinding(id, keyStroke, descriptionKey, dispatcher)
    }

    /**
     * 注册双击快捷键
     *
     * @param id 快捷键 ID
     * @param keyCode 要检测的键码（如 KeyEvent.VK_SHIFT）
     * @param descriptionKey i18n 翻译 key
     * @param interval 双击间隔时间（毫秒），默认 500ms
     * @param action 双击时的回调
     */
    fun registerDoubleShortcut(
        id: String,
        keyCode: Int,
        descriptionKey: String,
        interval: Long = 500L,
        action: () -> Unit
    ) {
        var lastPressTime = 0L

        val dispatcher = KeyEventDispatcher { e: KeyEvent ->
            // 只处理指定键的按下事件
            if (e.id == KeyEvent.KEY_PRESSED && e.keyCode == keyCode && !e.isConsumed) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastPress = currentTime - lastPressTime

                // 如果两次按下间隔在指定时间内，视为双击
                if (timeSinceLastPress > 0 && timeSinceLastPress < interval) {
                    // 触发双击回调
                    SwingUtilities.invokeLater { action() }
                    lastPressTime = 0 // 重置，避免连续触发
                    true // 消费事件
                } else {
                    // 记录本次按下时间
                    lastPressTime = currentTime
                    false // 不消费事件，让其他组件正常处理
                }
            } else {
                false // 不消费事件
            }
        }

        // 使用特殊的 KeyStroke 来表示双击（仅用于显示）
        val doubleKeyStroke = KeyStroke.getKeyStroke(keyCode, 0)
        registerCustomShortcut(
            id = id,
            keyStroke = doubleKeyStroke,
            descriptionKey = descriptionKey,
            dispatcher = dispatcher
        )
    }


    /**
     * 注销全局快捷键
     */
    fun unregisterShortcut(id: String) {
        globalBindings.remove(id)?.let { binding ->
            focusManager.removeKeyEventDispatcher(binding.dispatcher)
        }
    }

    /**
     * 注销所有快捷键
     */
    fun unregisterAll() {
        globalBindings.keys.toList().forEach { unregisterShortcut(it) }
    }

    /**
     * 获取所有已注册的快捷键列表
     */
    fun getAllShortcuts(): List<ShortcutBinding> {
        return globalBindings.values.toList()
    }

    /**
     * 根据 ID 查找快捷键
     */
    fun getShortcut(id: String): ShortcutBinding? {
        return globalBindings[id]
    }
}

