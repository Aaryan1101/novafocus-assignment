package com.novafocus.alphabetlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLogicTest {
    @Test fun letterCentersAndClamping() {
        assertEquals(10f, letterCenter(0, 10f, 20f))
        assertEquals(510f, letterCenter(25, 10f, 20f))
        assertEquals(0, letterIndex(-10f, 10f, 20f))
        assertEquals(25, letterIndex(999f, 10f, 20f))
        assertEquals(12, letterIndex(250f, 10f, 20f))
        assertEquals(0, letterIndex(19.9f, 10f, 20f))
        assertEquals(1, letterIndex(20.1f, 10f, 20f))
        assertEquals(-1, sidebarIndex(-10f, 10f, 20f))
        assertEquals(0, sidebarIndex(0.1f, 10f, 20f))
    }

    @Test fun groupingAndSortingAreCaseInsensitive() {
        assertEquals('A', groupKey("apple"))
        assertEquals(null, groupKey("123 emoji"))
        assertEquals(null, groupKey("# app"))
        assertEquals(listOf("alpha", "Beta", "zulu"), sortLabels(listOf("zulu", "Beta", "alpha")))
    }

    @Test fun curveIsSymmetricAndFadesWithDistance() {
        val center = curveDisplacement(100f, 100f, 20f, 100f)
        val near = curveDisplacement(120f, 100f, 20f, 100f)
        val far = curveDisplacement(400f, 100f, 20f, 100f)
        assertEquals(
            curveDisplacement(80f, 100f, 20f, 100f),
            curveDisplacement(120f, 100f, 20f, 100f),
            0.01f
        )
        assertTrue(center < near)
        assertTrue(near < far)
        assertTrue(kotlin.math.abs(far) < 0.01f)
    }

    @Test fun alphabetSpacingFitsShortCanvas() {
        val spacing = safeAlphabetSpacing(100f, 20f)
        val first = (100f - spacing * 25f) / 2f
        assertTrue(spacing < 18f)
        assertTrue(first - spacing >= 20f)
        assertTrue(first + 26f * spacing <= 80f)
    }

    @Test fun alphabetSpacingIsCappedOnTallCanvas() {
        assertEquals(32f, safeAlphabetSpacing(1600f, 20f, 32f))
    }
}
