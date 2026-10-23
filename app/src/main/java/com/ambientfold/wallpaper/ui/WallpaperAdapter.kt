package com.ambientfold.wallpaper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import com.ambientfold.wallpaper.databinding.ItemWallpaperCardBinding
import com.ambientfold.wallpaper.model.WallpaperItem

class WallpaperAdapter(
    private val items: List<WallpaperItem>,
    private var selectedId: String,
    private val onItemSelected: (WallpaperItem) -> Unit
) : RecyclerView.Adapter<WallpaperAdapter.WallpaperViewHolder>() {

    inner class WallpaperViewHolder(val binding: ItemWallpaperCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WallpaperViewHolder {
        val binding = ItemWallpaperCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WallpaperViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WallpaperViewHolder, position: Int) {
        val item = items[position]
        val isSelected = item.id == selectedId

        with(holder.binding) {
            txtWallpaperTitle.text = item.title
            txtWallpaperCategory.text = item.category.name.lowercase().replaceFirstChar { it.uppercase() }
            imgThumbnail.setImageDrawable(
                AppCompatResources.getDrawable(root.context, item.drawableResId)
            )

            imgSelectedBadge.visibility = if (isSelected) View.VISIBLE else View.GONE
            cardWallpaper.strokeWidth = if (isSelected) 6 else 0

            root.setOnClickListener {
                val previousSelected = selectedId
                selectedId = item.id
                notifyItemChanged(items.indexOfFirst { it.id == previousSelected })
                notifyItemChanged(position)
                onItemSelected(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateSelection(newId: String) {
        val oldIndex = items.indexOfFirst { it.id == selectedId }
        val newIndex = items.indexOfFirst { it.id == newId }
        selectedId = newId
        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }
}
