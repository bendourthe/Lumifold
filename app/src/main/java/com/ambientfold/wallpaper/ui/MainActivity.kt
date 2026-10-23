package com.ambientfold.wallpaper.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ambientfold.wallpaper.R
import com.ambientfold.wallpaper.databinding.ActivityMainBinding
import com.ambientfold.wallpaper.model.WallpaperCategory
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.service.FoldWallpaperService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedWallpaper: WallpaperItem? = null
    private var currentCategory: WallpaperCategory = WallpaperCategory.LANDSCAPES
    private lateinit var wallpaperAdapter: WallpaperAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedWallpaper = WallpaperRepository.getActiveWallpaper(this)
        currentCategory = selectedWallpaper?.category ?: WallpaperCategory.LANDSCAPES

        setupMockup()
        setupCategoryChips()
        setupWallpaperCarousel()
        setupEffectChips()
        setupMotionChips()
        setupApplyButton()
    }

    private fun setupMockup() {
        val isDynamic = WallpaperRepository.isDynamicMotionEnabled(this)
        selectedWallpaper?.let {
            binding.mockupFoldable.setWallpaper(it, isDynamic)
        }
        binding.mockupFoldable.setOpeningEffect(WallpaperRepository.getOpeningEffect(this))
        binding.mockupFoldable.setMotionEnabled(isDynamic)
    }

    private fun setupCategoryChips() {
        when (currentCategory) {
            WallpaperCategory.LANDSCAPES -> binding.chipCatLandscapes.isChecked = true
            WallpaperCategory.ABSTRACT_DYNAMIC -> binding.chipCatAbstract.isChecked = true
            WallpaperCategory.TEXTURES -> binding.chipCatTextures.isChecked = true
            else -> binding.chipCatLandscapes.isChecked = true
        }

        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            currentCategory = when (checkedIds.first()) {
                R.id.chipCatLandscapes -> WallpaperCategory.LANDSCAPES
                R.id.chipCatAbstract -> WallpaperCategory.ABSTRACT_DYNAMIC
                R.id.chipCatTextures -> WallpaperCategory.TEXTURES
                else -> WallpaperCategory.LANDSCAPES
            }
            updateWallpaperListForCategory()
        }
    }

    private fun setupWallpaperCarousel() {
        val activeItem = selectedWallpaper ?: WallpaperRepository.wallpapers.first()
        val categoryItems = WallpaperRepository.getWallpapersByCategory(currentCategory).ifEmpty {
            WallpaperRepository.wallpapers
        }

        wallpaperAdapter = WallpaperAdapter(
            items = categoryItems,
            selectedId = activeItem.id
        ) { item ->
            selectedWallpaper = item
            WallpaperRepository.setActiveWallpaper(this, item.id)
            val isDynamic = WallpaperRepository.isDynamicMotionEnabled(this)
            binding.mockupFoldable.setWallpaper(item, isDynamic)
        }

        binding.rvWallpapers.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvWallpapers.adapter = wallpaperAdapter
    }

    private fun updateWallpaperListForCategory() {
        val categoryItems = WallpaperRepository.getWallpapersByCategory(currentCategory)
        val activeId = selectedWallpaper?.id ?: ""
        wallpaperAdapter.setCategoryItems(categoryItems, activeId)
    }

    private fun setupEffectChips() {
        val currentEffect = WallpaperRepository.getOpeningEffect(this)
        when (currentEffect) {
            WallpaperRepository.EFFECT_DUO_BLUR -> binding.chipEffectDuo.isChecked = true
            WallpaperRepository.EFFECT_CREASE_BLOOM -> binding.chipEffectBloom.isChecked = true
            WallpaperRepository.EFFECT_LIQUID_GLASS -> binding.chipEffectLiquid.isChecked = true
            WallpaperRepository.EFFECT_APERTURE_HORIZON -> binding.chipEffectAperture.isChecked = true
            else -> binding.chipEffectDuo.isChecked = true
        }

        binding.chipGroupEffects.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedEffect = when (checkedIds.first()) {
                R.id.chipEffectDuo -> WallpaperRepository.EFFECT_DUO_BLUR
                R.id.chipEffectBloom -> WallpaperRepository.EFFECT_CREASE_BLOOM
                R.id.chipEffectLiquid -> WallpaperRepository.EFFECT_LIQUID_GLASS
                R.id.chipEffectAperture -> WallpaperRepository.EFFECT_APERTURE_HORIZON
                else -> WallpaperRepository.EFFECT_DUO_BLUR
            }
            WallpaperRepository.setOpeningEffect(this, selectedEffect)
            binding.mockupFoldable.setOpeningEffect(selectedEffect)
        }
    }

    private fun setupMotionChips() {
        val isDynamic = WallpaperRepository.isDynamicMotionEnabled(this)
        if (isDynamic) {
            binding.chipMotionDynamic.isChecked = true
        } else {
            binding.chipMotionStatic.isChecked = true
        }

        binding.chipGroupMotion.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val dynamic = checkedIds.first() == R.id.chipMotionDynamic
            WallpaperRepository.setDynamicMotionEnabled(this, dynamic)
            binding.mockupFoldable.setMotionEnabled(dynamic)
        }
    }

    private fun setupApplyButton() {
        binding.btnApplyWallpaper.setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, FoldWallpaperService::class.java)
                )
            }
            startActivity(intent)
        }
    }
}
