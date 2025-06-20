package org.example.agent

import ai.jetbrains.code.prompt.executor.clients.grazie.koog.model.JetBrainsAIModels
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.params.LLMParams
import org.example.utils.simpleGrazieExecutor


fun createFormattingAgent(systemPrompt: String): AIAgent {
    val promptExecutor = simpleGrazieExecutor(System.getenv("JWT_TOKEN"))

    fun onToolCallEvent(message: String) = { println("Tool called: $message") }

    val ToolRegistry = ToolRegistry {
        tool(Tools::retrieveTypstMathSymbols.asTool())
    }

    return AIAgent(
        promptExecutor = promptExecutor,
        toolRegistry = ToolRegistry,
        strategy = formattingStrategy(
            onAssistantMessage = {
                println("Assistant: $it")
                print("Response > ")
                readln()
            },
            listOf<ToolDescriptor>(Tools::retrieveTypstMathSymbols.asTool().descriptor)
        ),
        agentConfig = AIAgentConfig(
            prompt = prompt("system", params = LLMParams(temperature = 1.0)) {
                system(systemPrompt)
            },
            model = JetBrainsAIModels.Anthropic.Opus_4,
            maxAgentIterations = 100
        )
    ) {
        handleEvents {
            onToolCall { tool: Tool<*, *>, toolArgs: ToolArgs ->
                onToolCallEvent("Tool ${tool.name}, args ${toolArgs.toString().replace('\n', ' ').take(100)}...")
            }
        }
    }
}

fun formattingStrategy(
    onAssistantMessage: suspend (String) -> String,
    tools: List<ToolDescriptor>
) = strategy("formatting") {
    val generateAndValidateCode by subgraph<String, String>(
        toolSelectionStrategy = ToolSelectionStrategy.Tools(tools)
    ) {
        val nodeCallLLM by nodeLLMRequest()
        val executeToolCall by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()
        val staticCompiler by node<String, Pair<String, Boolean>> { code ->
            val result = Tools.compileTypstCode(code)
            println(result)
            if (result.isEmpty()) {
                Pair(code, true)
            } else {
                Pair(result, false)
            }
        }

        val validator by node<String, Pair<String, Boolean>> { code ->
            val result = Tools.callValidatingAgent(code).toString()
            println(result)
            if (!result.contains("INVALID")) {
               Pair(code, true)
            } else {
                Pair(result, false)
            }
        }

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo staticCompiler onAssistantMessage { true })
        edge(nodeCallLLM forwardTo executeToolCall onToolCall {
            true
        })
        edge(executeToolCall forwardTo sendToolResult)
        edge(sendToolResult forwardTo staticCompiler onAssistantMessage { true })
        edge(staticCompiler forwardTo nodeCallLLM onCondition { !it.second } transformed { it.first })
        edge(staticCompiler forwardTo validator onCondition { it.second } transformed { it.first })
        edge(validator forwardTo nodeFinish onCondition { it.second } transformed { it.first })
        edge(validator forwardTo nodeCallLLM onCondition { !it.second } transformed { it.first })
        edge(sendToolResult forwardTo executeToolCall onToolCall { true })


    }

    nodeStart then generateAndValidateCode then nodeFinish
}