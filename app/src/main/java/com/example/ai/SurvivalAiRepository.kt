package com.example.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SurvivalAiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are AETHER-MEDIC, an ultra-concise emergency field medic and disaster survival AI " +
                        "operating in zero-infrastructure crisis triage mode. " +
                        "STRICT OUTPUT RULES: " +
                        "1. Zero fluff, zero greetings, zero apologies, zero disclaimers. " +
                        "2. Calm, authoritative tactical tone. " +
                        "3. Always respond with numbered step-by-step life-saving instructions (maximum 6 concise steps). " +
                        "4. Prioritize immediate airway/breathing, massive hemorrhage control, crush/entrapment survival, " +
                        "field water purification, and hypothermia prevention. " +
                        "5. Include exact improvised materials when standard medical gear is unavailable."
                )
            },
            generationConfig = generationConfig {
                temperature = 0.25f
                maxOutputTokens = 550
            }
        )
    }

    fun streamTriageProtocol(query: String): Flow<String> {
        return model.generateContentStream(query).map { chunk ->
            chunk.text.orEmpty()
        }
    }
}
