package com.localllm.chat.llm.agent

import org.json.JSONObject

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
)

interface Tool {
    val name: String
    val description: String
    val parameters: List<ToolParameter>

    suspend fun execute(args: JSONObject): ToolResult

    fun toSchemaJson(): JSONObject {
        val properties = JSONObject()
        val required = mutableListOf<String>()

        for (param in parameters) {
            properties.put(param.name, JSONObject().apply {
                put("type", param.type)
                put("description", param.description)
            })
            if (param.required) {
                required.add(param.name)
            }
        }

        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", properties)
                    put("required", org.json.JSONArray(required))
                })
            })
        }
    }
}

data class ToolResult(
    val output: String,
    val isError: Boolean = false,
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject,
)
