package com.sms.edgenode.data.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class RoutingRule(
    val senderPattern: String? = null,
    val bodyPattern: String? = null,
    val forwardTarget: String? = null,
    val action: String = "HOLD",
    val maxForwardDepth: Int = 3
)

object RuleParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(ruleJson: String): RoutingRule {
        return try {
            json.decodeFromString<RoutingRule>(ruleJson)
        } catch (e: Exception) {
            RoutingRule(action = "HOLD")
        }
    }

    fun validate(rule: RoutingRule): Boolean {
        if (rule.action !in listOf("FORWARD", "HOLD", "DROP")) return false
        if (rule.action == "FORWARD" && rule.forwardTarget.isNullOrBlank()) return false
        if (rule.maxForwardDepth < 1 || rule.maxForwardDepth > 10) return false
        
        try {
            rule.senderPattern?.toRegex()
            rule.bodyPattern?.toRegex()
        } catch (e: Exception) {
            return false
        }
        
        return true
    }
}
