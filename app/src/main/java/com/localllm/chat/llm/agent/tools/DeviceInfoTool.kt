package com.localllm.chat.llm.agent.tools

import android.os.Build
import com.localllm.chat.llm.agent.Tool
import com.localllm.chat.llm.agent.ToolParameter
import com.localllm.chat.llm.agent.ToolResult
import org.json.JSONObject

class DeviceInfoTool : Tool {
    override val name = "device_info"
    override val description = "Get information about the device hardware and software."
    override val parameters = listOf(
        ToolParameter(
            "category",
            "string",
            "Category of info: 'all', 'hardware', 'software', 'memory'",
            required = false,
        ),
    )

    override suspend fun execute(args: JSONObject): ToolResult {
        val category = args.optString("category", "all")
        val runtime = Runtime.getRuntime()

        val info = buildString {
            if (category == "all" || category == "hardware") {
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Board: ${Build.BOARD}")
                appendLine("SOC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
                appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("Processors: ${runtime.availableProcessors()}")
            }
            if (category == "all" || category == "software") {
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Build: ${Build.DISPLAY}")
            }
            if (category == "all" || category == "memory") {
                val maxMem = runtime.maxMemory() / (1024 * 1024)
                val totalMem = runtime.totalMemory() / (1024 * 1024)
                val freeMem = runtime.freeMemory() / (1024 * 1024)
                appendLine("Max Memory: ${maxMem}MB")
                appendLine("Allocated: ${totalMem}MB")
                appendLine("Free: ${freeMem}MB")
            }
        }

        return ToolResult(info.trim())
    }
}
