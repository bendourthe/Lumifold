package com.lumifold.wallpaper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRepositoryTest {

    @Test
    fun defaultWallpaperIsFirstAndGenerative() {
        val first = WallpaperRepository.wallpapers.first()
        assertEquals(WallpaperRepository.DEFAULT_WALLPAPER_ID, first.id)
        assertTrue(first.isLive)
    }

    @Test
    fun resolveFallsBackToDefault() {
        val expected = WallpaperRepository.wallpapers.first().id
        assertEquals(expected, WallpaperRepository.resolveWallpaper(null).id)
        assertEquals(expected, WallpaperRepository.resolveWallpaper("  ").id)
        assertEquals(expected, WallpaperRepository.resolveWallpaper("mountain_sunset").id)
    }

    @Test
    fun catalogIntegrity() {
        val all = WallpaperRepository.wallpapers
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (item in all) {
            assertTrue(item.title.isNotBlank())
            assertTrue(item.description.isNotBlank())
            when (val s = item.source) {
                is WallpaperSource.Photo -> assertTrue(s.resId != 0)
                is WallpaperSource.Generative -> assertEquals(5, s.palette.colors.size)
                is WallpaperSource.Video -> {
                    assertTrue(
                        s.url.startsWith("https://upload.wikimedia.org/") ||
                            s.url.startsWith("https://github.com/bendourthe/lumifold/releases/download/") ||
                            s.url.startsWith("https://svs.gsfc.nasa.gov/")
                    )
                    assertTrue(s.posterResId != 0 && s.previewResId != 0 && s.sizeMb > 0f)
                }
            }
        }
    }

    @Test
    fun everyCategoryHasAtLeastThreeDesigns() {
        val all = WallpaperRepository.wallpapers
        assertEquals(all.map { it.id }.toSet(), WallpaperRepository.getWallpapersByCategory(null).map { it.id }.toSet())
        assertTrue(liveBeforeStill(WallpaperRepository.getWallpapersByCategory(null)))
        for (c in WallpaperCategory.entries) {
            val items = WallpaperRepository.getWallpapersByCategory(c)
            assertTrue("$c", items.size >= 3)
            assertTrue("$c live first", liveBeforeStill(items))
        }
    }

    @Test
    fun everyCategoryHasTenLiveAndTenStillDesigns() {
        val qualitySplit = setOf(WallpaperCategory.CITY, WallpaperCategory.LANDMARKS)
        for (c in WallpaperCategory.entries) {
            if (c in qualitySplit) continue
            val items = WallpaperRepository.getWallpapersByCategory(c)
            val live = items.count { it.isLive }
            assertTrue("$c has $live live", live >= 10)
            assertTrue("$c has ${items.size - live} still", items.size - live >= 10)
        }
    }

    @Test
    fun cityIsSkylinesAndLandmarksAreSites() {
        val city = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.CITY)
        val landmarks = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.LANDMARKS)
        val skylineIds = setOf(
            "chicago_day_night", "la_night", "london_waterloo", "shanghai_lujiazui",
            "hong_kong_sunset", "toronto_skyline_live", "toronto_night", "san_francisco",
            "london_skyline", "manhattan_skyline", "toronto_day", "paris_sunset", "tokyo_tower_night"
        )
        assertEquals(skylineIds, city.map { it.id }.toSet())
        assertTrue(city.none { it.id == "paris_from_eiffel" || it.id == "colosseum" || it.id == "eiffel_sunset" })
        assertTrue(landmarks.any { it.id == "taj_mahal" })
        assertTrue(landmarks.any { it.id == "golden_gate" })
        assertTrue(landmarks.any { it.id == "great_wall" })
        assertTrue(landmarks.count { it.isLive } >= 6)
        assertTrue(landmarks.count { !it.isLive } >= 8)
        val liveOnly = WallpaperRepository.getWallpapersByCategory(null, live = true)
        val stillOnly = WallpaperRepository.getWallpapersByCategory(null, live = false)
        assertTrue(liveOnly.all { it.isLive })
        assertTrue(stillOnly.all { it.isStill })
        assertEquals(WallpaperRepository.wallpapers.size, liveOnly.size + stillOnly.size)
    }

    @Test
    fun everyVideoHasABundledLoop() {
        val raw = java.io.File("src/main/res/raw")
        for (item in WallpaperRepository.wallpapers.filter { it.source is WallpaperSource.Video }) {
            assertTrue("${item.id} has no loop", java.io.File(raw, "wpl_${item.id}.mp4").isFile)
        }
    }

    @Test
    fun everySceneIsUsed() {
        val used = WallpaperRepository.wallpapers.mapNotNull { (it.source as? WallpaperSource.Generative)?.scene }.toSet()
        // Black hole and galaxy are NASA footage now. The procedural scenes remain in the renderer.
        val replaced = setOf(Scene.BLACK_HOLE, Scene.GALAXY)
        assertEquals(Scene.entries.toSet() - replaced, used)
    }

    @Test
    fun landscapesHoldNoDrawnScenes() {
        val landscapes = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.LANDSCAPES)
        assertTrue(landscapes.none { it.source is WallpaperSource.Generative })
        val illustrated = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.ILLUSTRATED)
        assertTrue(illustrated.isNotEmpty())
        assertTrue(illustrated.all {
            val photo = it.source as? WallpaperSource.Photo
            it.source is WallpaperSource.Generative ||
                (photo != null && photo.motion == MotionStyle.DEPTH && photo.depthResId != 0)
        })
    }

    @Test
    fun getWallpaperById() {
        assertNotNull(WallpaperRepository.getWallpaperById("great_wave"))
        assertNull(WallpaperRepository.getWallpaperById("invalid_id"))
    }

    @Test
    fun legacyEffectIdsMapToDuoFrost() {
        for (legacy in listOf("duo_blur", "crease_bloom", "aperture_zoom", "prismatic_wave", null, "")) {
            assertEquals(OpeningEffect.DUO_FROST, OpeningEffect.fromId(legacy))
        }
        for (e in OpeningEffect.entries) assertEquals(e, OpeningEffect.fromId(e.id))
    }

    @Test
    fun effectShaderIndicesAreUniqueAndContiguous() {
        assertEquals(OpeningEffect.entries.indices.toList(), OpeningEffect.entries.map { it.shaderIndex }.sorted())
    }
}

private fun liveBeforeStill(items: List<WallpaperItem>): Boolean {
    val firstStill = items.indexOfFirst { !it.isLive }
    return firstStill < 0 || items.drop(firstStill).none { it.isLive }
}
