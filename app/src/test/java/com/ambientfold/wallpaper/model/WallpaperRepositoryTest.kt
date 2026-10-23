package com.ambientfold.wallpaper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRepositoryTest {

    @Test
    fun testResolveWallpaperWithValidId() {
        val wallpaper = WallpaperRepository.resolveWallpaper("mountain_sunset")
        assertNotNull(wallpaper)
        assertEquals("Alpine Glacier Dusk", wallpaper.title)
        assertEquals(WallpaperCategory.LANDSCAPES, wallpaper.category)
        assertTrue("Must be dynamic", wallpaper.isDynamic)
    }

    @Test
    fun testResolveWallpaperWithNullOrEmptyFallsBackToFirst() {
        val fromNull = WallpaperRepository.resolveWallpaper(null)
        val fromEmpty = WallpaperRepository.resolveWallpaper("")
        val fromBlank = WallpaperRepository.resolveWallpaper("   ")

        val expectedDefault = WallpaperRepository.wallpapers.first()
        assertEquals(expectedDefault.id, fromNull.id)
        assertEquals(expectedDefault.id, fromEmpty.id)
        assertEquals(expectedDefault.id, fromBlank.id)
    }

    @Test
    fun testResolveWallpaperWithUnknownIdFallsBack() {
        val result = WallpaperRepository.resolveWallpaper("non_existent_id_xyz")
        val expectedDefault = WallpaperRepository.wallpapers.first()
        assertEquals("Unrecognized wallpaper ID must fall back to default", expectedDefault.id, result.id)
    }

    @Test
    fun testCatalogIntegrity() {
        val all = WallpaperRepository.wallpapers
        assertTrue("Catalog must contain at least 6 wallpapers", all.size >= 6)

        val uniqueIds = all.map { it.id }.toSet()
        assertEquals("All wallpaper IDs must be unique", all.size, uniqueIds.size)

        for (item in all) {
            assertTrue("Wallpaper title must not be blank", item.title.isNotBlank())
            assertTrue("Wallpaper description must not be blank", item.description.isNotBlank())
            assertTrue("Drawable resource ID must be non-zero", item.drawableResId != 0)
        }
    }

    @Test
    fun testCategoryFiltering() {
        val landscapes = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.LANDSCAPES)
        val abstractDynamic = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.ABSTRACT_DYNAMIC)

        assertTrue("Must contain landscape wallpapers", landscapes.size >= 4)
        assertTrue("Must contain abstract dynamic wallpapers", abstractDynamic.isNotEmpty())
    }

    @Test
    fun testGetWallpaperById() {
        val found = WallpaperRepository.getWallpaperById("dark_prism")
        assertNotNull(found)
        assertEquals("Obsidian Prism", found?.title)

        val notFound = WallpaperRepository.getWallpaperById("invalid_id")
        assertNull(notFound)
    }

    @Test
    fun testOpeningEffectConstants() {
        assertEquals("duo_blur", WallpaperRepository.EFFECT_DUO_BLUR)
        assertEquals("crease_bloom", WallpaperRepository.EFFECT_CREASE_BLOOM)
        assertEquals("aperture_zoom", WallpaperRepository.EFFECT_APERTURE_ZOOM)
        assertEquals("prismatic_wave", WallpaperRepository.EFFECT_PRISMATIC_WAVE)
    }
}
