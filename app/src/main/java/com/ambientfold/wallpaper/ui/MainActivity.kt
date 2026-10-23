package com.ambientfold.wallpaper.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ambientfold.wallpaper.R
import com.ambientfold.wallpaper.databinding.ActivityMainBinding
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.service.FoldWallpaperService
import com.ambientfold.wallpaper.service.HingeSensorObserver

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var hingeObserver: HingeSensorObserver? = null
    private var selectedWallpaper: WallpaperItem? = null
    private var isSimulating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedWallpaper = WallpaperRepository.getActiveWallpaper(this)

        setupMockup()
        setupEffectChips()
        setupMotionControls()
        setupWallpaperCarousel()
        setupSlider()
        setupApplyButton()

        hingeObserver = HingeSensorObserver(this) { _, angleDegrees ->
            if (!isSimulating) {
                runOnUiThread {
                    binding.mockupFoldable.setFoldAngleDirect(angleDegrees)
                    binding.sliderFoldAngle.value = angleDegrees
                    updateAngleBadge(angleDegrees)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hingeObserver?.isSensorAvailable == true) {
            hingeObserver?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        hingeObserver?.stop()
    }

    private fun setupMockup() {
        selectedWallpaper?.let {
            binding.mockupFoldable.setWallpaper(it)
        }
        binding.mockupFoldable.setOpeningEffect(WallpaperRepository.getOpeningEffect(this))
        binding.mockupFoldable.setDynamicMotionEnabled(WallpaperRepository.isDynamicMotionEnabled(this))

        binding.mockupFoldable.onAngleChangedListener = { angle ->
            updateAngleBadge(angle)
        }

        binding.btnPreviewUnfold.setOnClickListener {
            binding.mockupFoldable.playOpeningAnimation(WallpaperRepository.getAnimationDuration(this))
        }
    }

    private fun updateAngleBadge(angle: Float) {
        val label = when {
            angle >= 170f -> "${angle.toInt()}° Flat Open"
            angle in 75f..120f -> "${angle.toInt()}° Tabletop Mode"
            angle < 45f -> "${angle.toInt()}° Folded"
            else -> "${angle.toInt()}° Flex Mode"
        }
        binding.txtLiveAngleBadge.text = label
        binding.txtFoldSliderLabel.text = "Angle: ${angle.toInt()}°"
    }

    private fun setupEffectChips() {
        val currentEffect = WallpaperRepository.getOpeningEffect(this)
        when (currentEffect) {
            WallpaperRepository.EFFECT_DUO_BLUR -> binding.chipEffectDuo.isChecked = true
            WallpaperRepository.EFFECT_CREASE_BLOOM -> binding.chipEffectBloom.isChecked = true
            WallpaperRepository.EFFECT_APERTURE_ZOOM -> binding.chipEffectAperture.isChecked = true
            WallpaperRepository.EFFECT_PRISMATIC_WAVE -> binding.chipEffectPrismatic.isChecked = true
        }

        binding.chipGroupEffects.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedEffect = when (checkedIds.first()) {
                R.id.chipEffectDuo -> WallpaperRepository.EFFECT_DUO_BLUR
                R.id.chipEffectBloom -> WallpaperRepository.EFFECT_CREASE_BLOOM
                R.id.chipEffectAperture -> WallpaperRepository.EFFECT_APERTURE_ZOOM
                R.id.chipEffectPrismatic -> WallpaperRepository.EFFECT_PRISMATIC_WAVE
                else -> WallpaperRepository.EFFECT_DUO_BLUR
            }
            WallpaperRepository.setOpeningEffect(this, selectedEffect)
            binding.mockupFoldable.setOpeningEffect(selectedEffect)
            // Immediately preview the opening effect in the top mockup
            binding.mockupFoldable.playOpeningAnimation(WallpaperRepository.getAnimationDuration(this))
        }
    }

    private fun setupMotionControls() {
        val isMotionEnabled = WallpaperRepository.isDynamicMotionEnabled(this)
        binding.switchDynamicMotion.isChecked = isMotionEnabled
        binding.switchDynamicMotion.setOnCheckedChangeListener { _, isChecked ->
            WallpaperRepository.setDynamicMotionEnabled(this, isChecked)
            binding.mockupFoldable.setDynamicMotionEnabled(isChecked)
        }

        val currentBlur = WallpaperRepository.getMaxBlurRadius(this)
        when {
            currentBlur <= 40f -> binding.chipBlurSubtle.isChecked = true
            currentBlur >= 80f -> binding.chipBlurCinematic.isChecked = true
            else -> binding.chipBlurBalanced.isChecked = true
        }

        binding.chipGroupBlur.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val blur = when (checkedIds.first()) {
                R.id.chipBlurSubtle -> 35.0f
                R.id.chipBlurCinematic -> 95.0f
                else -> 65.0f
            }
            WallpaperRepository.setMaxBlurRadius(this, blur)
            binding.mockupFoldable.playOpeningAnimation(WallpaperRepository.getAnimationDuration(this))
        }
    }

    private fun setupWallpaperCarousel() {
        val activeItem = selectedWallpaper ?: WallpaperRepository.wallpapers.first()
        val adapter = WallpaperAdapter(
            items = WallpaperRepository.wallpapers,
            selectedId = activeItem.id
        ) { item ->
            selectedWallpaper = item
            WallpaperRepository.setActiveWallpaper(this, item.id)
            binding.mockupFoldable.setWallpaper(item)
            binding.mockupFoldable.playOpeningAnimation(WallpaperRepository.getAnimationDuration(this))
        }

        binding.rvWallpapers.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvWallpapers.adapter = adapter
    }

    private fun setupSlider() {
        binding.sliderFoldAngle.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                isSimulating = true
                binding.mockupFoldable.setFoldAngleDirect(value)
                updateAngleBadge(value)
            }
        }

        binding.sliderFoldAngle.addOnSliderTouchListener(object :
            com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                isSimulating = true
            }

            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                if (hingeObserver?.isSensorAvailable == true) {
                    binding.root.postDelayed({ isSimulating = false }, 1800)
                }
            }
        })
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
