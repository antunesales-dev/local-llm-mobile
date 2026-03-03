package com.localllm.chat.llm.agent

import com.localllm.chat.llm.ChatMessage
import com.localllm.chat.llm.GenerationParams
import com.localllm.chat.llm.LlmInference
import com.localllm.chat.llm.agent.tools.CalculatorTool
import com.localllm.chat.llm.agent.tools.DateTimeTool
import com.localllm.chat.llm.agent.tools.DeviceInfoTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AgentEvent {
    data class Token(val text: String) : AgentEvent
    data class ToolCallStart(val toolCall: ToolCall) : AgentEvent
    data class ToolCallResult(val toolCall: ToolCall, val result: ToolResult) : AgentEvent
    data class FinalResponse(val fullText: String) : AgentEvent
    data class Error(val message: String) : AgentEvent
}

@Singleton
class AgentExecutor @Inject constructor(
    private val llmInference: LlmInference,
) {
    private val tools: Map<String, Tool> = listOf(
        CalculatorTool(),
        DateTimeTool(),
        DeviceInfoTool(),
    ).associateBy { it.name }

    private val maxIterations = 5

    fun buildSystemPrompt(basePrompt: String): String {
        if (tools.isEmpty()) return basePrompt

        val toolDescriptions = tools.values.joinToString("\n") { tool ->
            val params = tool.parameters.joinToString(", ") { p ->
                "\"${p.name}\": \"<${p.type}: ${p.description}>\""
            }
            "- ${tool.name}: ${tool.description}\n  Parameters: { $params }"
        }

        return """$basePrompt

You have access to the following tools. To use a tool, respond with a JSON block in this exact format:
<tool_call>
{"name": "<tool_name>", "arguments": {<parameters>}}
</tool_call>

Available tools:
$toolDescriptions

After receiving a tool result, incorporate it into your response to the user.
If you don't need any tools, respond normally without tool_call blocks."""
    }

    fun execute(
        messages: List<ChatMessage>,
        params: GenerationParams = GenerationParams(),
    ): Flow<AgentEvent> = flow {
        var currentMessages = messages.toMutableList()
        var iteration = 0

        while (iteration < maxIterations) {
            val fullResponse = StringBuilder()

            llmInference.generateResponse(currentMessages, params).collect { token ->
                fullResponse.append(token)
                emit(AgentEvent.Token(token))
            }

            val responseText = fullResponse.toString()
            val toolCall = parseToolCall(responseText)

            if (toolCall == null) {
                emit(AgentEvent.FinalResponse(responseText))
                return@flow
            }

            val tool = tools[toolCall.name]
            if (tool == null) {
                emit(AgentEvent.Error("Unknown tool: ${toolCall.name}"))
                emit(AgentEvent.FinalResponse(responseText))
                return@flow
            }

            emit(AgentEvent.ToolCallStart(toolCall))

            val result = try {
                tool.execute(toolCall.arguments)
            } catch (e: Exception) {
                ToolResult("Tool execution failed: ${e.message}", isError = true)
            }

            emit(AgentEvent.ToolCallResult(toolCall, result))

            currentMessages.add(ChatMessage(role = "assistant", content = responseText))
            currentMessages.add(ChatMessage(
                role = "user",
                content = "Tool '${toolCall.name}' returned: ${result.output}",
            ))

            iteration++
        }

        emit(AgentEvent.Error("Max tool iterations ($maxIterations) reached"))
    }

    private fun parseToolCall(text: String): ToolCall? {
        val pattern = Regex("<tool_call>\\s*(.+?)\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(text) ?: return null

        return try {
            val json = JSONObject(match.groupValues[1].trim())
            ToolCall(
                id = "call_${System.currentTimeMillis()}",
                name = json.getString("name"),
                arguments = json.optJSONObject("arguments") ?: JSONObject(),
            )
        } catch (e: Exception) {
            null
        }
    }
}
