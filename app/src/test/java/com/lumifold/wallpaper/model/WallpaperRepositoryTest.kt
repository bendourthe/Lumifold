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
                            s.url.startsWith("https://github.com/bendourthe/lumifold/releases/download/")
                    )
                    assertTrue(s.posterResId != 0 && s.sizeMb > 0f)
                }
            }
        }
    }

    @Test
    fun everyCategoryHasAtLeastThreeDesigns() {
        for (c in WallpaperCategory.entries) {
            assertTrue("$c", WallpaperRepository.getWallpapersByCategory(c).size >= 3)
        }
        assertEquals(WallpaperRepository.wallpapers, WallpaperRepository.getWallpapersByCategory(null))
    }

    @Test
    fun everySceneIsUsed() {
        val used = WallpaperRepository.wallpapers.mapNotNull { (it.source as? WallpaperSource.Generative)?.scene }.toSet()
        assertEquals(Scene.entries.toSet(), used)
    }

    @Test
    fun landscapesHoldNoDrawnScenes() {
        val landscapes = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.LANDSCAPES)
        assertTrue(landscapes.none { it.source is WallpaperSource.Generative })
        val illustrated = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.ILLUSTRATED)
        assertTrue(illustrated.isNotEmpty())
        assertTrue(illustrated.all {
            val photo = it.source as? WallpaperSource.Photo
            photo != null && photo.motion == MotionStyle.DEPTH && photo.depthResId != 0
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
    }}
