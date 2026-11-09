package com.onetake.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotlightStageScaffoldTest {
    @Test
    fun testStageGeometryConstantsMatchSpec() {
        assertEquals(46f, SpotlightStageGeometry.STAGE_LIGHT_BAR_WIDTH_DP)
        assertEquals(5f, SpotlightStageGeometry.STAGE_LIGHT_BAR_HEIGHT_DP)
        assertEquals(68f, SpotlightStageGeometry.PRIMARY_CTA_DIAMETER_DP)
        assertEquals(10f, SpotlightStageGeometry.PRIMARY_CTA_HALO_WIDTH_DP)
        assertEquals(0.5f, SpotlightStageGeometry.HAIRLINE_WIDTH_DP)
        assertEquals(0.68f, SpotlightStageGeometry.LIT_STAGE_WEIGHT)
        assertEquals(0.32f, SpotlightStageGeometry.IN_THE_WINGS_WEIGHT)
    }
}
