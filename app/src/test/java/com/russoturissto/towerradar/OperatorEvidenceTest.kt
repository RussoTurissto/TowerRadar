package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class OperatorEvidenceTest {
    private fun site(extra: String?) = TowerSite("node/1",50.0,30.0,null,null,
        true,null,null,"",extraInfo=extra)
    @Test fun doesNotGuessWithoutEvidence() {
        assertNull(OperatorEvidence.inspect(site("Высокая мачта")).operator)
    }
    @Test fun suggestsOnlyExplicitNetworkTag() {
        val result = OperatorEvidence.inspect(site("Опора: решётка · Сеть: МТС"))
        assertEquals("МТС", result.operator)
        assertTrue(result.explanation.contains("Предположительно"))
    }
}
