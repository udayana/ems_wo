package com.sofindo.ems.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sofindo.ems.R

class PhotoGalleryAdapter(
    private val photoUris: List<Uri>,
    private val onRemovePhoto: (Int) -> Unit
) : RecyclerView.Adapter<PhotoGalleryAdapter.PhotoViewHolder>() {
    
    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPhoto: ImageView = itemView.findViewById(R.id.iv_photo)
        val btnRemove: ImageButton = itemView.findViewById(R.id.btn_remove_photo)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_gallery, parent, false)
        return PhotoViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= photoUris.size) {
            return
        }
        val photoUri = photoUris[position]
        
        Glide.with(holder.itemView.context)
            .load(photoUri)
            .centerCrop()
            .into(holder.ivPhoto)
        
        holder.btnRemove.setOnClickListener {
            onRemovePhoto(position)
        }
    }
    
    override fun getItemCount(): Int {
        return try {
            photoUris.size
        } catch (e: Exception) {
            0
        }
    }
}

