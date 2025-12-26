package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.store.Store
import editorx.gui.Theme
import editorx.gui.ThemeManager
import java.awt.BorderLayout
import java.awt.Font
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingUtilities

class AppearancePanel(private val settings: Store) : SettingsPanel() {
    
    // 保存原始值，用于比较是否有实际更改
    private var originalLocale: Locale? = null
    private var originalTheme: Theme? = null
    private val languageButtons = mutableMapOf<Locale, JRadioButton>()
    private val languageGroup = ButtonGroup()
    private val lightThemeButton = JRadioButton()
    private val darkThemeButton = JRadioButton()
    private val headerLabel = JLabel()
    private val languagePanel = JPanel()
    private val themePanel = JPanel()
    
    companion object {
        private const val LOCALE_KEY = "ui.locale"
        private const val THEME_KEY = "ui.theme"
        private const val PENDING_LOCALE = "pending.locale"
        private const val PENDING_THEME = "pending.theme"
    }

    init {
        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 16f)
        headerLabel.border = BorderFactory.createEmptyBorder(0, 0, 12, 0)
        
        // 主题按钮分组
        ButtonGroup().apply {
            add(lightThemeButton)
            add(darkThemeButton)
        }

        languagePanel.layout = BoxLayout(languagePanel, BoxLayout.Y_AXIS)
        
        themePanel.layout = BoxLayout(themePanel, BoxLayout.Y_AXIS)
        lightThemeButton.alignmentX = LEFT_ALIGNMENT
        darkThemeButton.alignmentX = LEFT_ALIGNMENT
        lightThemeButton.addActionListener { changeTheme(Theme.Light) }
        darkThemeButton.addActionListener { changeTheme(Theme.Dark) }
        themePanel.add(lightThemeButton)
        themePanel.add(Box.createVerticalStrut(8))
        themePanel.add(darkThemeButton)
        
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(languagePanel)
            add(Box.createVerticalStrut(24))
            add(themePanel)
        }

        contentPanel.add(headerLabel, BorderLayout.NORTH)
        contentPanel.add(mainPanel, BorderLayout.CENTER)

        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }
        updateTheme()

        refresh()
    }

    override fun refresh() {
        // 从设置中读取保存的语言（可能是未生效的新语言）
        val savedLocaleTag = settings.get(LOCALE_KEY, null)
        val savedLocale = savedLocaleTag?.let { 
            runCatching { Locale.forLanguageTag(it) }.getOrNull() 
        } ?: I18n.locale()
        
        // 保存原始值
        originalLocale = savedLocale
        originalTheme = ThemeManager.currentTheme
        
        // 如果有待保存的语言，使用待保存的；否则使用保存的
        val displayLocale = getPendingChange<Locale>(PENDING_LOCALE) ?: savedLocale
        
        // 更新语言选项按钮
        updateLanguageButtons(displayLocale)
        
        // 更新界面文本（使用当前运行时的语言）
        headerLabel.text = I18n.translate(I18nKeys.Settings.APPEARANCE)
        languagePanel.border = BorderFactory.createTitledBorder(I18n.translate(I18nKeys.Settings.LANGUAGE))
        themePanel.border = BorderFactory.createTitledBorder(I18n.translate(I18nKeys.Settings.THEME))
        
        // 主题设置：直接使用当前主题（主题已立即生效，没有待保存的概念）
        when (ThemeManager.currentTheme) {
            is Theme.Light -> lightThemeButton.isSelected = true
            is Theme.Dark -> darkThemeButton.isSelected = true
        }
        
        lightThemeButton.text = I18n.translate(I18nKeys.Theme.LIGHT)
        darkThemeButton.text = I18n.translate(I18nKeys.Theme.DARK)
        
        // 更新主题颜色
        updateTheme()
        
        // 更新按钮可见性
        updateRevertButtonVisibility()
    }
    
    /**
     * 检查是否有实际的待保存更改（与原始值比较）
     * 注意：主题更改已立即生效，不参与此检查
     */
    override fun hasActualPendingChanges(): Boolean {
        val pendingLocale = getPendingChange<Locale>(PENDING_LOCALE)
        
        // 主题更改已立即生效，不检查主题
        val localeChanged = pendingLocale != null && pendingLocale != originalLocale
        
        return localeChanged
    }

    private fun updateLanguageButtons(currentLocale: Locale) {
        val availableLocales = I18n.getAvailableLocales()
        val theme = ThemeManager.currentTheme
        
        // 移除旧的按钮
        languagePanel.removeAll()
        // 从 ButtonGroup 中移除所有按钮
        languageButtons.values.forEach { languageGroup.remove(it) }
        languageButtons.clear()
        
        // 创建新的按钮
        availableLocales.forEachIndexed { index, locale ->
            val button = JRadioButton().apply {
                alignmentX = LEFT_ALIGNMENT
                text = getLanguageDisplayName(locale)
                foreground = theme.onSurface
                addActionListener { changeLocale(locale) }
            }
            languageButtons[locale] = button
            languageGroup.add(button)
            languagePanel.add(button)
            if (index < availableLocales.size - 1) {
                languagePanel.add(Box.createVerticalStrut(8))
            }
        }
        
        // 最后设置选中状态（必须在添加到 ButtonGroup 之后）
        languageButtons[currentLocale]?.isSelected = true
        
        languagePanel.revalidate()
        languagePanel.repaint()
    }

    private fun getLanguageDisplayName(locale: Locale): String {
        // 尝试从翻译中获取语言名称
        val langKey = I18nKeys.Lang.forLocale(locale)
        
        val translated = I18n.translate(langKey)
        // 如果翻译返回的是 key 本身，使用 Locale 的显示名称（用当前界面语言显示）
        // 这样即使新增语言包，现有语言包也不需要更新
        return if (translated == langKey) {
            val currentLocale = I18n.locale()
            locale.getDisplayName(currentLocale)
        } else {
            translated
        }
    }

    private fun changeLocale(locale: Locale) {
        // 只更新UI选中状态，不立即保存
        languageButtons.values.forEach { it.isSelected = false }
        languageButtons[locale]?.isSelected = true
        
        // 记录待保存的语言
        setPendingChange(PENDING_LOCALE, locale)
    }
    
    private fun changeTheme(theme: Theme) {
        // 更新UI选中状态
        when (theme) {
            is Theme.Light -> lightThemeButton.isSelected = true
            is Theme.Dark -> darkThemeButton.isSelected = true
        }
        
        // 立即应用主题
        if (ThemeManager.currentTheme != theme) {
            ThemeManager.currentTheme = theme
            settings.put(THEME_KEY, ThemeManager.getThemeName(theme))
            settings.sync()
        }
        
        // 主题已立即生效，更新原始主题值，不记录为待保存的更改
        // 这样不会触发"还原更改"按钮
        originalTheme = theme
        // 清除可能存在的待保存主题更改
        clearPendingChange(PENDING_THEME)
    }
    
    /**
     * 应用并保存所有更改
     * @return 如果语言改变需要重启，返回 true
     */
    override fun applyChanges(): Boolean {
        var needRestart = false
        
        // 保存语言设置
        getPendingChange<Locale>(PENDING_LOCALE)?.let { locale ->
            val currentLocaleTag = settings.get(LOCALE_KEY, null)
            val currentLocale = currentLocaleTag?.let { 
                runCatching { Locale.forLanguageTag(it) }.getOrNull() 
            }
            
            if (currentLocale != locale) {
                settings.put(LOCALE_KEY, locale.toLanguageTag())
                needRestart = true
            }
        }
        
        // 主题已经在 changeTheme 中立即应用了，这里只需要清除待保存的更改
        // 如果主题已经应用，ThemeManager.currentTheme == theme，不会重复应用
        
        // 清除所有待保存的更改
        clearPendingChanges()
        
        settings.sync()
        return needRestart
    }

    /**
     * 还原所有更改
     * 注意：主题更改已立即生效，不需要还原
     */
    override fun revertChanges() {
        // 主题更改已立即生效，不需要还原
        // 只还原语言设置等其他待保存的更改
        super.revertChanges()
    }
    
    /**
     * 显示重启提示对话框
     * @return 如果用户选择重启，返回 true
     */
    fun showRestartDialog(): Boolean {
        val options = arrayOf(
            I18n.translate(I18nKeys.Dialog.RESTART),
            I18n.translate(I18nKeys.Dialog.LATER)
        )
        
        val result = JOptionPane.showOptionDialog(
            SwingUtilities.getWindowAncestor(this),
            I18n.translate(I18nKeys.Dialog.RESTART_REQUIRED_MESSAGE),
            I18n.translate(I18nKeys.Dialog.RESTART_REQUIRED),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0] // 默认选中"重启"
        )
        
        return result == JOptionPane.YES_OPTION
    }

    fun resetToDefault() {
        val defaultLocale = Locale.SIMPLIFIED_CHINESE
        val localeToSet = if (I18n.getAvailableLocales().contains(defaultLocale)) {
            defaultLocale
        } else {
            // 如果没有简体中文，使用第一个可用语言
            I18n.getAvailableLocales().firstOrNull() ?: return
        }
        
        // 先刷新界面
        refresh()
        
        // 然后设置待保存的值
        changeLocale(localeToSet)
        changeTheme(Theme.Light)
    }
    
    /**
     * 更新主题相关的颜色
     */
    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        
        // 更新标题标签颜色
        headerLabel.foreground = theme.onSurface
        
        // 更新语言面板和主题面板的边框颜色
        val languageBorder = languagePanel.border
        if (languageBorder is javax.swing.border.TitledBorder) {
            val title = languageBorder.title
            val newBorder = BorderFactory.createTitledBorder(title) as javax.swing.border.TitledBorder
            newBorder.titleColor = theme.onSurface
            languagePanel.border = newBorder
        }
        
        val themeBorder = themePanel.border
        if (themeBorder is javax.swing.border.TitledBorder) {
            val title = themeBorder.title
            val newBorder = BorderFactory.createTitledBorder(title) as javax.swing.border.TitledBorder
            newBorder.titleColor = theme.onSurface
            themePanel.border = newBorder
        }
        
        // 更新单选按钮颜色
        lightThemeButton.foreground = theme.onSurface
        darkThemeButton.foreground = theme.onSurface
        
        // 更新语言按钮颜色
        languageButtons.values.forEach { button ->
            button.foreground = theme.onSurface
        }
        
        // 更新面板背景
        languagePanel.background = theme.surface
        themePanel.background = theme.surface
        background = theme.surface
        contentPanel.background = theme.surface
    }
}
