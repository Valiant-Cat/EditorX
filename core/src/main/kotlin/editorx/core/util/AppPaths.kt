package editorx.core.util

import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Path

/**
 * EditorX 运行时路径推导工具。
 *
 * 约定：
 * - 对于 Gradle `installDist` 分发包：`<appHome>/lib/<jar>` 与 `<appHome>/plugins/<jar>`
 * - 对于 jpackage `app-image`：默认推导为 `<appHome>/plugins`（其中 appHome 为 jar 所在目录）
 * - 对于开发态（classes 目录）：回退到 `user.dir` 下的相对 `plugins/`
 */
object AppPaths {
    private val logger = LoggerFactory.getLogger(AppPaths::class.java)

    private const val HOME_PROP = "editorx.home"
    private const val HOME_ENV = "EDITORX_HOME"

    /**
     * 返回应用 Home 目录。
     *
     * 优先级：
     * 1) `-Deditorx.home=/path/to/appHome`
     * 2) 环境变量 `EDITORX_HOME=/path/to/appHome`
     * 3) 根据 core jar 位置推导（installDist: lib/..；否则 jar 所在目录）
     * 4) 回退到当前工作目录（`user.dir`）
     */
    fun appHome(): Path {
        return appHomeFromPropertyOrEnv()
            ?: appHomeFromCodeSource()
            ?: Path.of(System.getProperty("user.dir", "."))
    }

    /**
     * 返回插件目录。
     *
     * 约定：
     * - installDist: `<appHome>/plugins`
     * - jpackage app-image: `<appHome>/plugins`
     * - 开发态：`<user.dir>/plugins`
     */
    fun pluginsDir(): Path {
        return appHome().resolve("plugins")
    }

    /**
     * 返回内置工具目录（如 apktool.jar / smali.jar 等）。
     */
    fun toolsDir(): Path {
        return appHome().resolve("tools")
    }

    /**
     * 返回用户自定义工具链目录（优先放置可执行文件）。
     */
    fun toolchainDir(): Path {
        return appHome().resolve("toolchain")
    }

    private fun appHomeFromPropertyOrEnv(): Path? {
        val prop = System.getProperty(HOME_PROP)?.trim().orEmpty()
        if (prop.isNotEmpty()) return Path.of(prop)

        val env = System.getenv(HOME_ENV)?.trim().orEmpty()
        if (env.isNotEmpty()) return Path.of(env)

        return null
    }

    private fun appHomeFromCodeSource(): Path? {
        val uri = runCatching { AppPaths::class.java.protectionDomain?.codeSource?.location?.toURI() }
            .getOrNull()
            ?: return null

        val path = runCatching { pathFromUri(uri) }
            .onFailure { e -> logger.debug("无法解析 codeSource 路径: {}", uri, e) }
            .getOrNull()
            ?: return null

        // 开发态：class 输出目录（通常为 build/classes/...），不要推导到该目录，直接交给 user.dir 回退
        if (path.toFile().isDirectory) return null

        val parent = path.parent ?: return null
        return if (parent.fileName?.toString() == "lib") {
            // installDist: <appHome>/lib/core.jar -> <appHome>
            parent.parent
        } else {
            // 其它 jar 布局：以 jar 所在目录作为 appHome
            parent
        }
    }

    private fun pathFromUri(uri: URI): Path {
        // 兼容 `file:/...` 与 `file:...` 等格式
        return if (uri.scheme.equals("file", ignoreCase = true)) {
            Path.of(uri)
        } else {
            Path.of(uri.toString())
        }
    }
}
