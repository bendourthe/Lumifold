package com.ambientfold.wallpaper.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
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
        setupWallpaperCarousel()
        setupSlider()
        setupApplyButton()
        updateLivePreview(180f)

        hingeObserver = HingeSensorObserver(this) { progress, angleDegrees ->
            if (!isSimulating) {
                runOnUiThread {
                    binding.txtSensorStatus.text =
                        getString(R.string.sensor_status_active, angleDegrees.toInt())
                    binding.sliderFoldAngle.value = angleDegrees
                    binding.txtFoldSliderLabel.text =
                        getString(R.string.slider_hinge_label, angleDegrees.toInt())
                    updateLivePreview(angleDegrees)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hingeObserver?.isSensorAvailable == true) {
            hingeObserver?.start()
        } else {
            binding.txtSensorStatus.text = getString(R.string.sensor_status_simulated)
        }
    }

    override fun onPause() {
        super.onPause()
        hingeObserver?.stop()
    }

    private fun setupWallpaperCarousel() {
        val activeItem = selectedWallpaper ?: WallpaperRepository.wallpapers.first()
        val adapter = WallpaperAdapter(
            items = WallpaperRepository.wallpapers,
            selectedId = activeItem.id
        ) { item ->
            selectedWallpaper = item
            WallpaperRepository.setActiveWallpaper(this, item.id)
            val currentAngle = binding.sliderFoldAngle.value
            updateLivePreview(currentAngle)
        }

        binding.rvWallpapers.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvWallpapers.adapter = adapter
    }

    private fun setupSlider() {
        binding.sliderFoldAngle.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                isSimulating = true
                binding.txtFoldSliderLabel.text = getString(R.string.slider_hinge_label, value.toInt())
                updateLivePreview(value)
            }
        }

        binding.sliderFoldAngle.addOnSliderTouchListener(object :
            com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                isSimulating = true
            }

            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                // Return to hardware sensor readings after touch if sensor is present
                if (hingeObserver?.isSensorAvailable == true) {
                    binding.root.postDelayed({ isSimulating = false }, 1500)
                }
            }
        })
    }

    private fun updateLivePreview(angleDegrees: Float) {
        val item = selectedWallpaper ?: return
        binding.imgPreviewLive.setImageDrawable(
            AppCompatResources.getDrawable(this, item.drawableResId)
        )
        binding.txtLiveAngleBadge.text = "${angleDegrees.toInt()}°"

        val progress = (angleDegrees / 180f).coerceIn(0f, 1f)
        val maxBlur = WallpaperRepository.getMaxBlurRadius(this)
        val blurRadius = (1.0f - progress) * maxBlur

        // Scale zoom simulation
        val scale = 1.0f + (1.0f - progress) * 0.05f
        binding.imgPreviewLive.scaleX = scale
        binding.imgPreviewLive.scaleY = scale

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blurRadius > 0.5f) {
                val blurEffect = RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
                binding.imgPreviewLive.setRenderEffect(blurEffect)
            } else {
                binding.imgPreviewLive.setRenderEffect(null)
            }
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
