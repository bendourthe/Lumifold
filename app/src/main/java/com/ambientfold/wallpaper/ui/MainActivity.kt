package com.ambientfold.wallpaper.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.ambientfold.wallpaper.R
import com.ambientfold.wallpaper.databinding.ActivityMainBinding
import com.ambientfold.wallpaper.model.DuoBlurStateManager
import com.ambientfold.wallpaper.model.WallpaperCategory
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.service.FoldWallpaperService

class MainActivity : AppCompatActivity(), DuoBlurStateManager.TelemetryListener {

    private lateinit var binding: ActivityMainBinding
    private var selectedWallpaper: WallpaperItem? = null
    private var currentCategory: WallpaperCategory = WallpaperCategory.ABSTRACT_DYNAMIC
    private lateinit var wallpaperAdapter: WallpaperAdapter
    private var isUpdatingSliderFromMockup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent Outer Cover Display from turning off when cracking the hinge open (0° to 90°)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Edge-to-edge support with safe window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Prevent overlap with phone clock and top bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContentContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBars.top + 16,
                bottom = systemBars.bottom + 32
            )
            insets
        }

        selectedWallpaper = WallpaperRepository.getActiveWallpaper(this)
        currentCategory = selectedWallpaper?.category ?: WallpaperCategory.ABSTRACT_DYNAMIC

        setupMockup()
        setupMockupControls()
        setupCategorySegmentedTabs()
        setupWallpaperCarousel()
        setupEffectChips()
        setupMotionSegmentedTabs()
        setupApplyButton()

        DuoBlurStateManager.addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        DuoBlurStateManager.removeListener(this)
    }

    override fun onTelemetryUpdated(
        angle: Float,
        isInner: Boolean,
        displayId: Int,
        frostProgress: Float,
        phaseLabel: String
    ) {
        // Real-Time Hardware Telemetry HUD
        binding.txtHudAngle.text = String.format("%.1f°", angle)
        binding.txtHudPhase.text = phaseLabel
        binding.progressHudFrost.progress = (frostProgress * 100).toInt().coerceIn(0, 100)
        binding.txtHudFrostPercent.text = String.format("%.2f", frostProgress)

        binding.txtHudDisplayId.text = if (isInner) {
            "Display 1: Inner Main (Flexible)"
        } else {
            "Display 0: Outer Cover (Single)"
        }

        binding.txtHudCreaseAxis.text = if (isInner) {
            "Crease Axis: x = 50% (Center)"
        } else {
            "Hinge Axis: x = 0% (Left Edge)"
        }
    }

    private fun setupMockup() {
        val isDynamic = WallpaperRepository.isDynamicMotionEnabled(this)
        selectedWallpaper?.let {
            binding.mockupFoldable.setWallpaper(it, isDynamic)
        }
        binding.mockupFoldable.setOpeningEffect(WallpaperRepository.getOpeningEffect(this))
        binding.mockupFoldable.setMotionEnabled(isDynamic)

        // Sync slider and HUD when the 3D book mockup auto-loops
        binding.mockupFoldable.onAngleChangedListener = { angle, phaseLabel, frostProgress ->
            if (!binding.sliderHingeAngle.isFocused && !isUpdatingSliderFromMockup) {
                isUpdatingSliderFromMockup = true
                binding.sliderHingeAngle.value = angle
                isUpdatingSliderFromMockup = false
            }
            DuoBlurStateManager.updateHingeAngle(angle)
        }
    }

    private fun setupMockupControls() {
        binding.sliderHingeAngle.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.mockupFoldable.pauseAutoLoop()
                binding.mockupFoldable.setAngle(value)
                DuoBlurStateManager.updateHingeAngle(value)
            }
        }

        binding.btnAngleClosed.setOnClickListener {
            binding.mockupFoldable.pauseAutoLoop()
            binding.sliderHingeAngle.value = 0f
            binding.mockupFoldable.setAngle(0f)
            DuoBlurStateManager.updateHingeAngle(0f)
        }

        binding.btnAngleHandoff.setOnClickListener {
            binding.mockupFoldable.pauseAutoLoop()
            binding.sliderHingeAngle.value = 90f
            binding.mockupFoldable.setAngle(90f)
            DuoBlurStateManager.updateHingeAngle(90f)
        }

        binding.btnAngleOpen.setOnClickListener {
            binding.mockupFoldable.pauseAutoLoop()
            binding.sliderHingeAngle.value = 180f
            binding.mockupFoldable.setAngle(180f)
            DuoBlurStateManager.updateHingeAngle(180f)
        }

        binding.btnAngleAuto.setOnClickListener {
            binding.mockupFoldable.startCinematicLoop()
        }
    }

    private fun setupCategorySegmentedTabs() {
        updateCategoryTabStyles()

        binding.tabCatAbstract.setOnClickListener {
            currentCategory = WallpaperCategory.ABSTRACT_DYNAMIC
            updateCategoryTabStyles()
            updateWallpaperListForCategory()
        }

        binding.tabCatLandscapes.setOnClickListener {
            currentCategory = WallpaperCategory.LANDSCAPES
            updateCategoryTabStyles()
            updateWallpaperListForCategory()
        }

        binding.tabCatTextures.setOnClickListener {
            currentCategory = WallpaperCategory.TEXTURES
            updateCategoryTabStyles()
            updateWallpaperListForCategory()
        }
    }

    private fun updateCategoryTabStyles() {
        val activeBg = R.drawable.bg_tab_active
        val inactiveBg = R.drawable.bg_tab_inactive
        val activeColor = Color.WHITE
        val inactiveColor = Color.parseColor("#94A3B8")

        binding.tabCatAbstract.setBackgroundResource(
            if (currentCategory == WallpaperCategory.ABSTRACT_DYNAMIC) activeBg else inactiveBg
        )
        binding.tabCatAbstract.setTextColor(
            if (currentCategory == WallpaperCategory.ABSTRACT_DYNAMIC) activeColor else inactiveColor
        )

        binding.tabCatLandscapes.setBackgroundResource(
            if (currentCategory == WallpaperCategory.LANDSCAPES) activeBg else inactiveBg
        )
        binding.tabCatLandscapes.setTextColor(
            if (currentCategory == WallpaperCategory.LANDSCAPES) activeColor else inactiveColor
        )

        binding.tabCatTextures.setBackgroundResource(
            if (currentCategory == WallpaperCategory.TEXTURES) activeBg else inactiveBg
        )
        binding.tabCatTextures.setTextColor(
            if (currentCategory == WallpaperCategory.TEXTURES) activeColor else inactiveColor
        )
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

    private fun setupMotionSegmentedTabs() {
        val isDynamic = WallpaperRepository.isDynamicMotionEnabled(this)
        updateMotionTabStyles(isDynamic)

        binding.tabMotionDynamic.setOnClickListener {
            WallpaperRepository.setDynamicMotionEnabled(this, true)
            binding.mockupFoldable.setMotionEnabled(true)
            updateMotionTabStyles(true)
        }

        binding.tabMotionStatic.setOnClickListener {
            WallpaperRepository.setDynamicMotionEnabled(this, false)
            binding.mockupFoldable.setMotionEnabled(false)
            updateMotionTabStyles(false)
        }
    }

    private fun updateMotionTabStyles(isDynamic: Boolean) {
        val activeBg = R.drawable.bg_tab_active
        val inactiveBg = R.drawable.bg_tab_inactive
        val activeColor = Color.WHITE
        val inactiveColor = Color.parseColor("#94A3B8")

        binding.tabMotionDynamic.setBackgroundResource(if (isDynamic) activeBg else inactiveBg)
        binding.tabMotionDynamic.setTextColor(if (isDynamic) activeColor else inactiveColor)

        binding.tabMotionStatic.setBackgroundResource(if (!isDynamic) activeBg else inactiveBg)
        binding.tabMotionStatic.setTextColor(if (!isDynamic) activeColor else inactiveColor)
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
