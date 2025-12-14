package com.sofindo.ems.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.models.MaterialItem
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class MaterialsAdapter(
    private var materials: List<MaterialItem>,
    private val onMaterialChange: (MaterialItem) -> Unit,
    private val onRemoveMaterial: (MaterialItem) -> Unit
) : RecyclerView.Adapter<MaterialsAdapter.MaterialViewHolder>() {
    
    class MaterialViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val etMaterialName: EditText = itemView.findViewById(R.id.et_material_name)
        val etQuantity: EditText = itemView.findViewById(R.id.et_quantity)
        val etUnit: EditText = itemView.findViewById(R.id.et_unit)
        val etUnitPrice: EditText = itemView.findViewById(R.id.et_unit_price)
        val tvAmount: TextView = itemView.findViewById(R.id.tv_amount)
        val btnRemove: Button = itemView.findViewById(R.id.btn_remove_material)
        var textWatcher: TextWatcher? = null
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MaterialViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_material_row, parent, false)
        return MaterialViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MaterialViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= materials.size) {
            return
        }
        
        val material = materials[position]
        
        holder.etMaterialName.setText(material.materialName)
        holder.etQuantity.setText(material.quantity)
        holder.etUnit.setText(material.unit)
        holder.etUnitPrice.setText(material.unitPrice)
        
        updateAmount(holder, material)
        
        // Remove previous listener if exists
        holder.textWatcher?.let { watcher ->
            holder.etMaterialName.removeTextChangedListener(watcher)
            holder.etQuantity.removeTextChangedListener(watcher)
            holder.etUnit.removeTextChangedListener(watcher)
            holder.etUnitPrice.removeTextChangedListener(watcher)
        }
        
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                material.materialName = holder.etMaterialName.text.toString()
                material.quantity = holder.etQuantity.text.toString()
                material.unit = holder.etUnit.text.toString()
                material.unitPrice = holder.etUnitPrice.text.toString()
                updateAmount(holder, material)
                onMaterialChange(material)
            }
        }
        
        holder.textWatcher = textWatcher
        
        holder.etMaterialName.addTextChangedListener(textWatcher)
        holder.etQuantity.addTextChangedListener(textWatcher)
        holder.etUnit.addTextChangedListener(textWatcher)
        holder.etUnitPrice.addTextChangedListener(textWatcher)
        
        holder.btnRemove.setOnClickListener {
            onRemoveMaterial(material)
        }
    }
    
    private fun updateAmount(holder: MaterialViewHolder, material: MaterialItem) {
        val symbols = DecimalFormatSymbols(Locale("id", "ID"))
        symbols.currencySymbol = "Rp "
        val formatter = DecimalFormat("#,##0.00", symbols)
        holder.tvAmount.text = "Amount: Rp ${formatter.format(material.amount)}"
    }
    
    override fun getItemCount(): Int {
        return try {
            materials.size
        } catch (e: Exception) {
            0
        }
    }
    
    fun updateMaterials(newMaterials: List<MaterialItem>) {
        materials = newMaterials
        try {
            notifyDataSetChanged()
        } catch (e: Exception) {
            android.util.Log.e("MaterialsAdapter", "Error in notifyDataSetChanged: ${e.message}", e)
        }
    }
}

