package org.example.agent

import ai.jetbrains.code.prompt.executor.clients.grazie.koog.model.JetBrainsAIModels
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import org.example.utils.simpleGrazieExecutor

class ValidatingAgent(val systemPrompt: String) {
    fun createValidatingAgent(): AIAgent {
        return AIAgent(
            promptExecutor = simpleGrazieExecutor(System.getenv("JWT_TOKEN")),
            toolRegistry = ToolRegistry {
            },
            strategy = singleRunStrategy(),
            agentConfig = AIAgentConfig(
                prompt = prompt("system") {
                    system(systemPrompt)
                },
                model = JetBrainsAIModels.Anthropic.Sonnet_3_7,
                maxAgentIterations = 100
            )
        )
    }
}