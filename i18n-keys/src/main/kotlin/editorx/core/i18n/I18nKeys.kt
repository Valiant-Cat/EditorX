package editorx.core.i18n

/**
 * 翻译 key 常量类。
 * 所有翻译 key 都应在此定义，避免在代码中硬编码字符串。
 *
 * 使用方式：
 * ```kotlin
 * I18n.translate(I18nKeys.Menu.FILE)
 * ```
 *
 * 新增翻译 key 时：
 * 1. 在此类中添加对应的常量
 * 2. 在语言包插件中添加翻译
 */
object I18nKeys {

    /**
     * 菜单相关的翻译 key
     */
    object Menu {
        const val FILE = "menu.file"
        const val EDIT = "menu.edit"
        const val PLUGINS = "menu.plugins"
        const val HELP = "menu.help"
        const val LANGUAGE = "menu.language"
    }

    /**
     * 操作相关的翻译 key
     */
    object Action {
        const val OPEN_FILE = "action.openFile"
        const val OPEN_FOLDER = "action.openFolder"
        const val RECENT = "action.recent"
        const val SAVE = "action.save"
        const val SAVE_AS = "action.saveAs"
        const val EXIT = "action.exit"
        const val FIND = "action.find"
        const val REPLACE = "action.replace"
        const val GLOBAL_SEARCH = "action.globalSearch"
        const val PLUGIN_MANAGER = "action.pluginManager"
        const val ABOUT = "action.about"
        const val HELP = "action.help"
        const val UNDO = "action.undo"
        const val REDO = "action.redo"
        const val NEW_FILE = "action.newFile"
        const val NEW_FOLDER = "action.newFolder"
        const val DELETE = "action.delete"
        const val REFRESH = "action.refresh"
        const val REVEAL_IN_SYSTEM = "action.revealInSystem"
        const val COPY_PATH = "action.copyPath"
        const val SELECT_IN_SIDEBAR = "action.selectInSidebar"
        const val REVEAL_IN_EXPLORER = "action.revealInExplorer"
        const val CLOSE = "action.close"
        const val CLOSE_OTHERS = "action.closeOthers"
        const val CLOSE_ALL = "action.closeAll"
        const val CLOSE_LEFT = "action.closeLeft"
        const val CLOSE_RIGHT = "action.closeRight"
        const val CLOSE_UNMODIFIED = "action.closeUnmodified"
        const val FORMAT_FILE = "action.formatFile"
        const val RESET = "action.reset"
        const val CANCEL = "action.cancel"
        const val CONFIRM = "action.confirm"
        const val INSTALL_PLUGIN = "action.installPlugin"
        const val OPEN_PLUGINS_FOLDER = "action.openPluginsFolder"
        const val ENABLE = "action.enable"
        const val DISABLE = "action.disable"
        const val UNINSTALL = "action.uninstall"
        const val REVERT_CHANGES = "action.revertChanges"
    }

    /**
     * 设置相关的翻译 key
     */
    object Settings {
        const val TITLE = "settings.title"
        const val PREFERENCES = "settings.preferences"
        const val APPEARANCE = "settings.appearance"
        const val LANGUAGE = "settings.language"
        const val THEME = "settings.theme"
        const val APPEARANCE_TIP = "settings.appearance.tip"
        const val KEYMAP = "settings.keymap"
        const val PLUGINS = "settings.plugins"
        const val CACHE = "settings.cache"
        const val KEYMAP_TITLE = "settings.keymap.title"
        const val KEYMAP_HINT = "settings.keymap.hint"
        const val ADD_NOTE = "settings.keymap.addNote"
        const val ADD_NOTE_TOOLTIP = "settings.keymap.addNoteTooltip"
        const val EXPORT = "settings.keymap.export"
        const val EXPORT_TOOLTIP = "settings.keymap.exportTooltip"
        const val CACHE_TITLE = "settings.cache.title"
        const val CACHE_HINT = "settings.cache.hint"
        const val REFRESH_CACHE = "settings.cache.refresh"
        const val CLEAR_SELECTED = "settings.cache.clearSelected"
        const val OPEN_FOLDER = "settings.cache.openFolder"
        const val PLUGIN_STATE_DISABLED = "settings.plugins.state.disabled"
        const val PLUGIN_STATE_ENABLED = "settings.plugins.state.enabled"
        const val PLUGIN_STATE_FAILED = "settings.plugins.state.failed"
    }

    /**
     * 缓存表格相关的翻译 key
     */
    object CacheTable {
        const val NAME = "cache.table.name"
        const val PATH = "cache.table.path"
        const val SIZE = "cache.table.size"
        const val DESCRIPTION = "cache.table.description"
    }

    /**
     * 主题相关的翻译 key
     */
    object Theme {
        const val LIGHT = "theme.light"
        const val DARK = "theme.dark"
    }

    /**
     * 语言名称相关的翻译 key。
     * 格式：lang.{locale.toLanguageTag()}
     * 例如：lang.zh-CN, lang.zh-TW, lang.en, lang.ja 等
     */
    object Lang {
        private const val PREFIX = "lang"

        /**
         * 根据 Locale 生成语言名称的 key
         */
        fun forLocale(locale: java.util.Locale): String {
            return "${PREFIX}.${locale.toLanguageTag()}"
        }
    }

    /**
     * 状态和消息相关的翻译 key
     */
    object Status {
        const val READY = "status.ready"
        const val CANCEL = "status.cancel"
        const val GOTO_LINE_COLUMN = "status.gotoLineColumn"
        const val ERROR = "status.error"
        const val WARNING = "status.warning"
        const val SUCCESS = "status.success"
        const val VERSION_CONTROL = "status.versionControl"
        const val NO_FILE_OPENED = "status.noFileOpened"
        const val LINE_COLUMN = "status.lineColumn"
    }

    /**
     * 查找替换相关的翻译 key
     */
    object FindReplace {
        const val SEARCH = "findReplace.search"
        const val REPLACE = "findReplace.replace"
        const val EXPAND_REPLACE = "findReplace.expandReplace"
        const val COLLAPSE_REPLACE = "findReplace.collapseReplace"
        const val MATCH_CASE = "findReplace.matchCase"
        const val WHOLE_WORD = "findReplace.wholeWord"
        const val REGEX = "findReplace.regex"
        const val REPLACE_ONE = "findReplace.replaceOne"
        const val REPLACE_ALL = "findReplace.replaceAll"
        const val REPLACE_ONE_TOOLTIP = "findReplace.replaceOneTooltip"
        const val REPLACE_ALL_TOOLTIP = "findReplace.replaceAllTooltip"
        const val FIND_PREV = "findReplace.findPrev"
        const val FIND_NEXT = "findReplace.findNext"
        const val CLOSE = "findReplace.close"
        const val NO_RESULTS = "findReplace.noResults"
        const val RESULTS = "findReplace.results"
        const val INVALID_REGEX = "findReplace.invalidRegex"
        const val SEARCH_FAILED = "findReplace.searchFailed"
        const val REPLACE_FAILED = "findReplace.replaceFailed"
    }

    /**
     * 对话框和提示相关的翻译 key
     */
    object Dialog {
        const val SELECT_FOLDER = "dialog.selectFolder"
        const val SELECT_PLUGIN_JAR = "dialog.selectPluginJar"
        const val SELECT_FILE = "dialog.selectFile"
        const val TIP = "dialog.tip"
        const val INFO = "dialog.info"
        const val ABOUT_TITLE = "dialog.about.title"
        const val ABOUT_MESSAGE = "dialog.about.message"
        const val HELP_NOT_IMPLEMENTED = "dialog.help.notImplemented"
        const val NO_RECENT_FILES = "dialog.noRecentFiles"
        const val FILE_NOT_EXISTS = "dialog.fileNotExists"
        const val NOT_FOUND = "dialog.notFound"
        const val ERROR = "dialog.error"
        const val SELECT_ENTRY_FIRST = "dialog.selectEntryFirst"
        const val DIRECTORY_NOT_FOUND = "dialog.directoryNotFound"
        const val CLEAR_CACHE = "dialog.clearCache"
        const val CLEARED = "dialog.cleared"
        const val CLEAR_FAILED = "dialog.clearFailed"
        const val UNABLE_TO_OPEN = "dialog.unableToOpen"
        const val PATH_NOT_EXISTS = "dialog.pathNotExists"
        const val UNABLE_TO_OPEN_SYSTEM = "dialog.unableToOpenSystem"
        const val COPY_PATH_FAILED = "dialog.copyPathFailed"
        const val PATH_COPIED = "dialog.pathCopied"
        const val PLUGIN_SYSTEM_NOT_INIT = "dialog.pluginSystemNotInit"
        const val WORKSPACE_NOT_OPENED = "dialog.workspaceNotOpened"
        const val SELECT_JAR_FILE = "dialog.selectJarFile"
        const val CONFIRM_OVERWRITE = "dialog.confirmOverwrite"
        const val CANNOT_UNINSTALL = "dialog.cannotUninstall"
        const val CONFIRM_REMOVAL = "dialog.confirmRemoval"
        const val UNINSTALL_FAILED = "dialog.uninstallFailed"
        const val COPY_FAILED = "dialog.copyFailed"
        const val NO_PLUGIN_ENTRY = "dialog.noPluginEntry"
        const val INSTALLED_AND_STARTED = "dialog.installedAndStarted"
        const val DELETE_RECENT_PROJECT = "dialog.deleteRecentProject"
        const val OPEN_APK_FILE = "dialog.openApkFile"
        const val DETECTED_APK = "dialog.detectedApk"
        const val LANGUAGE_CHANGED = "dialog.languageChanged"
        const val LANGUAGE_CHANGED_MESSAGE = "dialog.languageChangedMessage"
        const val RESTART_REQUIRED = "dialog.restartRequired"
        const val RESTART_REQUIRED_MESSAGE = "dialog.restartRequiredMessage"
        const val RESTART = "dialog.restart"
        const val LATER = "dialog.later"
        const val OPEN_AAR_FILE = "dialog.openAarFile"
        const val DETECTED_AAR = "dialog.detectedAar"
        const val AAR_DIR_EXISTS = "dialog.aarDirExists"
        const val AAR_DIR_EXISTS_TITLE = "dialog.aarDirExistsTitle"
        const val OPEN_EXISTING_PROJECT = "dialog.openExistingProject"
        const val REEXTRACT = "dialog.reextract"
        const val AAR_EXTRACT_FAILED = "dialog.aarExtractFailed"
    }

    /**
     * 资源管理器相关的翻译 key
     */
    object Explorer {
        const val TITLE = "explorer.title"
        const val NEW_FILE = "explorer.newFile"
        const val NEW_FOLDER = "explorer.newFolder"
        const val DELETE = "explorer.delete"
        const val REFRESH = "explorer.refresh"
        const val REVEAL_IN_SYSTEM = "explorer.revealInSystem"
        const val VIEW_MODE_PROJECT = "explorer.viewMode.project"
        const val WORKSPACE_NOT_OPENED = "explorer.workspaceNotOpened"
    }

    /**
     * 编辑器相关的翻译 key
     */
    object Editor {
        const val CLOSE = "editor.close"
        const val CLOSE_OTHERS = "editor.closeOthers"
        const val CLOSE_ALL = "editor.closeAll"
        const val CLOSE_LEFT = "editor.closeLeft"
        const val CLOSE_RIGHT = "editor.closeRight"
        const val CLOSE_UNMODIFIED = "editor.closeUnmodified"
        const val FORMAT_FILE = "editor.formatFile"
        const val CANNOT_READ_FILE = "editor.cannotReadFile"
        const val TOTAL_FILES = "editor.totalFiles"
        const val CANNOT_READ_ARCHIVE = "editor.cannotReadArchive"
    }

    /**
     * 工具栏相关的翻译 key
     */
    object Toolbar {
        const val GOTO_MANIFEST = "toolbar.gotoManifest"
        const val GOTO_MAIN_ACTIVITY = "toolbar.gotoMainActivity"
        const val GOTO_APPLICATION = "toolbar.gotoApplication"
        const val EDIT_APP_INFO = "toolbar.editAppInfo"
        const val EDIT_APP_LOCALES = "toolbar.editAppLocales"
        const val EDIT_APP_ICONS = "toolbar.editAppIcons"
        const val BUILD = "toolbar.build"
        const val TOGGLE_SIDEBAR = "toolbar.toggleSidebar"
        const val TOGGLE_AGENT_PANEL = "toolbar.toggleAgentPanel"
        const val GLOBAL_SEARCH = "toolbar.globalSearch"
        const val SETTINGS = "toolbar.settings"
        const val DOUBLE_SHIFT = "toolbar.doubleShift"
    }

    /**
     * 搜索相关的翻译 key
     */
    object Search {
        const val SEARCH = "search.search"
        const val STOP = "search.stop"
        const val SEARCH_LABEL = "search.searchLabel"
        const val PLEASE_ENTER_SEARCH = "search.pleaseEnterSearch"
        const val PLEASE_OPEN_FOLDER = "search.pleaseOpenFolder"
        const val SCANNED_FILES = "search.scannedFiles"
    }

    /**
     * 欢迎页面相关的翻译 key
     */
    object Welcome {
        const val RECENT_PROJECTS = "welcome.recentProjects"
        const val VIEW_ALL = "welcome.viewAll"
        const val NO_RECENT_PROJECTS = "welcome.noRecentProjects"
        const val NEW_FILE = "welcome.newFile"
        const val OPEN_FILE = "welcome.openFile"
        const val OPEN_PROJECT = "welcome.openProject"
    }

    /**
     * 导航栏相关的翻译 key
     */
    object Navigation {
        const val NO_FILE_OPENED = "navigation.noFileOpened"
        const val COPY_PATH = "navigation.copyPath"
        const val SELECT_IN_SIDEBAR = "navigation.selectInSidebar"
        const val REVEAL_IN_EXPLORER = "navigation.revealInExplorer"
    }

    /**
     * 插件面板相关的翻译 key
     */
    object Plugins {
        const val NO_PLUGIN_SELECTED = "plugins.noPluginSelected"
        const val ID = "plugins.id"
        const val VERSION = "plugins.version"
        const val ORIGIN = "plugins.origin"
        const val STATE = "plugins.state"
        const val PATH = "plugins.path"
        const val PLUGINS_COUNT = "plugins.pluginsCount"
        const val SCAN_COMPLETED = "plugins.scanCompleted"
        const val ENABLED = "plugins.enabled"
        const val DISABLED = "plugins.disabled"
        const val REMOVED = "plugins.removed"
        const val BUNDLED = "plugins.bundled"
        const val BUILTIN_CANNOT_UNINSTALL = "plugins.builtinCannotUninstall"
        const val CONFIRM_UNINSTALL = "plugins.confirmUninstall"
        const val UNINSTALL_FAILED = "plugins.uninstallFailed"
        const val NO_PLUGIN_ENTRY = "plugins.noPluginEntry"
        const val INSTALLED_AND_STARTED = "plugins.installedAndStarted"
    }

    /**
     * 快捷键面板相关的翻译 key
     */
    object Keymap {
        const val ACTION = "keymap.action"
        const val SHORTCUT = "keymap.shortcut"
        const val DESCRIPTION = "keymap.description"
    }

    /**
     * 快捷键描述相关的翻译 key
     */
    object Shortcut {
        const val GLOBAL_SEARCH = "shortcut.globalSearch"
        const val OPEN_SETTINGS = "shortcut.openSettings"
        const val FIND = "shortcut.find"
        const val REPLACE = "shortcut.replace"
        const val SAVE = "shortcut.save"
        const val CLOSE_TAB = "shortcut.closeTab"
        const val FORMAT_FILE = "shortcut.formatFile"
    }

    /**
     * 工具栏相关的消息翻译 key
     */
    object ToolbarMessage {
        const val COMPILING = "toolbarMessage.compiling"
        const val COMPILING_TITLE = "toolbarMessage.compilingTitle"
        const val WORKSPACE_NOT_OPENED = "toolbarMessage.workspaceNotOpened"
        const val NOT_APKTOOL_DIR = "toolbarMessage.notApktoolDir"
        const val CANNOT_COMPILE = "toolbarMessage.cannotCompile"
        const val COMPILING_APK = "toolbarMessage.compilingApk"
        const val SIGNING_APK = "toolbarMessage.signingApk"
        const val COMPILE_AND_SIGN_SUCCESS = "toolbarMessage.compileAndSignSuccess"
        const val APK_GENERATED = "toolbarMessage.apkGenerated"
        const val COMPILE_COMPLETE = "toolbarMessage.compileComplete"
        const val EXTRACTING_AAR = "toolbarMessage.extractingAar"
        const val DECOMPILING_AAR = "toolbarMessage.decompilingAar"
        const val PACKING_AAR = "toolbarMessage.packingAar"
        const val PACK_AAR_FAILED = "toolbarMessage.packAarFailed"
        const val REBUILDING_AAR_CLASSES = "toolbarMessage.rebuildingAarClasses"
        const val REBUILD_AAR_CLASSES_FAILED = "toolbarMessage.rebuildAarClassesFailed"
        const val SIGN_FAILED = "toolbarMessage.signFailed"
        const val SIGN_FAILED_DETAIL = "toolbarMessage.signFailedDetail"
        const val APKTOOL_NOT_FOUND = "toolbarMessage.apktoolNotFound"
        const val APKTOOL_NOT_FOUND_DETAIL = "toolbarMessage.apktoolNotFoundDetail"
        const val COMPILE_CANCELLED = "toolbarMessage.compileCancelled"
        const val COMPILE_FAILED = "toolbarMessage.compileFailed"
        const val COMPILE_FAILED_DETAIL = "toolbarMessage.compileFailedDetail"
        const val COMPILE_EXCEPTION = "toolbarMessage.compileException"
        const val KEYSTORE_NOT_FOUND = "toolbarMessage.keystoreNotFound"
        const val APKSIGNER_NOT_FOUND = "toolbarMessage.apksignerNotFound"
        const val SIGN_EXCEPTION = "toolbarMessage.signException"
        const val MANIFEST_NOT_FOUND = "toolbarMessage.manifestNotFound"
        const val MAINACTIVITY_NOT_FOUND = "toolbarMessage.mainActivityNotFound"
        const val MAINACTIVITY_NOT_FOUND_DETAIL = "toolbarMessage.mainActivityNotFoundDetail"
        const val MAINACTIVITY_SMALI_NOT_FOUND = "toolbarMessage.mainActivitySmaliNotFound"
        const val PARSE_MANIFEST_FAILED = "toolbarMessage.parseManifestFailed"
        const val APPLICATION_NOT_FOUND = "toolbarMessage.applicationNotFound"
        const val APPLICATION_NOT_FOUND_DETAIL = "toolbarMessage.applicationNotFoundDetail"
        const val APPLICATION_SMALI_NOT_FOUND = "toolbarMessage.applicationSmaliNotFound"
        const val NO_BUILD_PROVIDER = "toolbarMessage.noBuildProvider"
        const val BUILD_TOOL_NOT_FOUND = "toolbarMessage.buildToolNotFound"
        const val COMPILE_SUCCESS = "toolbarMessage.compileSuccess"
        const val BUILD_GENERATED = "toolbarMessage.buildGenerated"
    }

    /**
     * 缓存相关的翻译 key
     */
    object Cache {
        const val CACHE_CONTENT = "cache.cacheContent"
        const val LOGS = "cache.logs"
        const val CACHE_DESC = "cache.cacheDesc"
        const val LOGS_DESC = "cache.logsDesc"
        const val CONFIRM_CLEAR = "cache.confirmClear"
        const val CANNOT_DELETE = "cache.cannotDelete"
    }
}
