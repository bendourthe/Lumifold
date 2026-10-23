package com.ambientfold.wallpaper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRepositoryTest {

    @Test
    fun testResolveWallpaperWithValidId() {
        val wallpaper = WallpaperRepository.resolveWallpaper("dunes_sunset")
        assertNotNull(wallpaper)
        assertEquals("Dunes Horizon", wallpaper.title)
        assertEquals(WallpaperCategory.LANDSCAPES, wallpaper.category)
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
        val geometric = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.GEOMETRIC)
        val minimal = WallpaperRepository.getWallpapersByCategory(WallpaperCategory.MINIMAL)

        assertEquals("Must contain 2 landscape wallpapers", 2, landscapes.size)
        assertEquals("Must contain 2 geometric wallpapers", 2, geometric.size)
        assertEquals("Must contain 2 minimal wallpapers", 2, minimal.size)
    }

    @Test
    fun testGetWallpaperById() {
        val found = WallpaperRepository.getWallpaperById("geometric_prism")
        assertNotNull(found)
        assertEquals("Prismatic Crystal", found?.title)

        val notFound = WallpaperRepository.getWallpaperById("invalid_id")
        assertNull(notFound)
    }
}
