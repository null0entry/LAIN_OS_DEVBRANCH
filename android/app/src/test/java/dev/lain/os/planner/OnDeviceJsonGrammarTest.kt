package dev.lain.os.planner

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OnDeviceJsonGrammarTest {
    private fun request(vararg names: String) = JSONObject().put("capabilities",
        JSONArray(names.map { JSONObject().put("name", it) })).toString()

    @Test fun proposalsUseOnlySuppliedNamesAndNoHistoricalIds() {
        val grammar = OnDeviceJsonGrammar.planner(request("android.toast", "android.vibrate"))
        assertTrue(grammar.contains("\\\"android.toast\\\""))
        assertTrue(grammar.contains("\\\"android.vibrate\\\""))
        assertFalse(grammar.contains("\\\"capability\\\""))
        assertFalse(grammar.contains("\\\"id\\\""))
        assertTrue(grammar.contains("terminal"))
        assertTrue(grammar.toByteArray().size <= 16_384)
    }

    @Test fun grammarInjectionAndDuplicateNamesAreRejected() {
        for (name in listOf("android.toast\" | hacked", "bad\nroot ::= hacked", "a".repeat(129))) {
            assertThrows(IllegalArgumentException::class.java) { OnDeviceJsonGrammar.planner(request(name)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnDeviceJsonGrammar.planner(request("android.toast", "android.toast"))
        }
    }

    @Test fun emptyCatalogCannotProduceActions() {
        val grammar = OnDeviceJsonGrammar.planner(request())
        assertTrue(grammar.startsWith("root ::= terminal"))
        assertFalse(grammar.contains("action ::="))
    }

    @Test fun routingCannotCarryActionsOrApprovalAndBoundsText() {
        val grammar = OnDeviceJsonGrammar.routing()
        assertFalse(grammar.contains("actions"))
        assertFalse(grammar.contains("approval"))
        assertTrue(grammar.contains("{1,48}"))
        assertTrue(grammar.contains("{0,200}"))
    }
}
