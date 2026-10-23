package com.ambientfold.wallpaper.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import com.ambientfold.wallpaper.databinding.ItemWallpaperCardBinding
import com.ambientfold.wallpaper.model.WallpaperItem

class WallpaperAdapter(
    private var items: List<WallpaperItem>,
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
            txtWallpaperCategory.text = item.category.displayName
            imgThumbnail.setImageDrawable(
                AppCompatResources.getDrawable(root.context, item.drawableResId)
            )

            imgSelectedBadge.visibility = if (isSelected) View.VISIBLE else View.GONE
            cardWallpaper.strokeColor = if (isSelected) Color.parseColor("#818CF8") else Color.parseColor("#253248")
            cardWallpaper.strokeWidth = if (isSelected) 4 else 2

            root.setOnClickListener {
                val previousSelected = selectedId
                selectedId = item.id
                val oldIndex = items.indexOfFirst { it.id == previousSelected }
                if (oldIndex != -1) notifyItemChanged(oldIndex)
                notifyItemChanged(position)
                onItemSelected(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun setCategoryItems(newItems: List<WallpaperItem>, currentSelectedId: String) {
        items = newItems
        selectedId = currentSelectedId
        notifyDataSetChanged()
    }

    fun updateSelection(newId: String) {
        val oldIndex = items.indexOfFirst { it.id == selectedId }
        val newIndex = items.indexOfFirst { it.id == newId }
        selectedId = newId
        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }
}
