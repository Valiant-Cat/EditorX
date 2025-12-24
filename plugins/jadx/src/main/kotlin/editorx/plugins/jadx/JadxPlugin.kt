package editorx.plugins.jadx

import editorx.core.plugin.ActivationEvent
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo
import editorx.core.services.DecompilerService
import editorx.core.toolchain.JadxTool
import org.slf4j.LoggerFactory
import java.io.File

class JadxPlugin : Plugin {
    private val logger = LoggerFactory.getLogger(JadxPlugin::class.java)
    private var serviceRegistered = false

    override fun getInfo(): PluginInfo = PluginInfo(
        id = "jadx",
        name = "JADX Decompiler",
        version = "0.1.0",
    )

    override fun activate(pluginContext: PluginContext) {
        if (serviceRegistered) return
        val service = object : DecompilerService {
            override fun decompile(input: File, outputDir: File, options: Map<String, Any?>): DecompilerService.DecompileResult {
                val cancelSignal = options["cancelSignal"] as? (() -> Boolean)
                val result = JadxTool.decompile(input, outputDir, cancelSignal)
                return when (result.status) {
                    JadxTool.Status.SUCCESS -> DecompilerService.DecompileResult(true, outputDir = outputDir)
                    JadxTool.Status.CANCELLED -> DecompilerService.DecompileResult(false, "cancelled")
                    JadxTool.Status.NOT_FOUND -> DecompilerService.DecompileResult(false, "jadx not found")
                    JadxTool.Status.FAILED -> DecompilerService.DecompileResult(false, result.output)
                }
            }
        }
        pluginContext.registerService(DecompilerService::class.java, service)
        serviceRegistered = true
        logger.info("JADX DecompilerService registered.")
    }

    override fun deactivate() {
        // ServiceRegistry 自动清理由 PluginManager 负责，在此处无需额外处理。
        serviceRegistered = false
    }

    override fun activationEvents(): List<ActivationEvent> =
        listOf(ActivationEvent.OnCommand("decompiler"))
}
