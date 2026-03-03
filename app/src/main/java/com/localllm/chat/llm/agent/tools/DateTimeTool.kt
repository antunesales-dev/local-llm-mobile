package com.localllm.chat.llm.agent.tools

import com.localllm.chat.llm.agent.Tool
import com.localllm.chat.llm.agent.ToolParameter
import com.localllm.chat.llm.agent.ToolResult
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DateTimeTool : Tool {
    override val name = "datetime"
    override val description = "Get the current date, time, or timezone information."
    override val parameters = listOf(
        ToolParameter(
            "query",
            "string",
            "What to retrieve: 'now' for current date/time, 'date' for date only, 'time' for time only, 'timezone' for timezone info",
        ),
    )

    override suspend fun execute(args: JSONObject): ToolResult {
        val query = args.optString("query", "now")
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()

        val result = when (query) {
            "date" -> now.format(DateTimeFormatter.ISO_LOCAL_DATE)
            "time" -> now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            "timezone" -> "Timezone: $zone, Offset: ${zone.rules.getOffset(now)}"
            else -> {
                val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                "$formatted ($zone)"
            }
        }

        return ToolResult(result)
    }
}
