package com.sofindo.ems.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.Utility
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch
import java.io.IOException

class UtilityFragment : Fragment() {
    
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvError: TextView
    private lateinit var btnProfile: android.widget.ImageButton
    
    private var utilities = mutableListOf<Utility>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_utility, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        loadUtilities()
    }
    
    private fun initViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        recyclerView = view.findViewById(R.id.recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        tvEmpty = view.findViewById(R.id.tv_empty)
        tvError = view.findViewById(R.id.tv_error)
        btnProfile = view.findViewById(R.id.btn_profile)
        
        toolbar.title = "Utility List"
        
        // Setup Profile Button
        btnProfile.setOnClickListener {
            navigateToProfile()
        }
    }
    
    private fun navigateToProfile() {
        val profileFragment = ProfileFragment()
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, profileFragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun loadUtilities() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val propID = UserService.getCurrentPropID()
                if (propID.isNullOrEmpty()) {
                    showError("Property ID not found")
                    return@launch
                }
                
                val response = RetrofitClient.apiService.getUtilities(propID)
                
                utilities = response.map { map ->
                    Utility(
                        id = (map["id"] as? Number)?.toInt() ?: 0,
                        propID = map["propID"] as? String ?: "",
                        codeName = map["codeName"] as? String ?: "",
                        category = map["category"] as? String ?: "",
                        utilityName = map["utilityName"] as? String ?: "",
                        satuan = map["satuan"] as? String ?: "",
                        folder = map["folder"] as? String ?: "",
                        location = map["location"] as? String ?: "",
                        link = map["link"] as? String ?: "",
                        icon = map["icon"] as? String ?: ""
                    )
                }.toMutableList()
                
                if (utilities.isEmpty()) {
                    showEmpty()
                } else {
                    setupRecyclerView()
                    showContent()
                }
                
            } catch (e: Exception) {
                Log.e("UtilityFragment", "Error loading utilities", e)
                showError("Failed to load utilities: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun setupRecyclerView() {
        // Group utilities by category
        val groupedUtilities = utilities.groupBy { it.category }
        val sortedCategories = groupedUtilities.keys.sorted()
        
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerView.adapter = UtilityCategoryAdapter(sortedCategories, groupedUtilities) { utility ->
            onUtilityClick(utility)
        }
    }
    
    private fun onUtilityClick(utility: Utility) {
        // Debug: Print all parameters
        Log.d("UtilityFragment", "=== UTILITY PARAMETERS ===")
        Log.d("UtilityFragment", "ID: ${utility.id}")
        Log.d("UtilityFragment", "PropID: ${utility.propID}")
        Log.d("UtilityFragment", "CodeName: ${utility.codeName}")
        Log.d("UtilityFragment", "Category: ${utility.category}")
        Log.d("UtilityFragment", "UtilityName: ${utility.utilityName}")
        Log.d("UtilityFragment", "Satuan: ${utility.satuan}")
        Log.d("UtilityFragment", "Folder: ${utility.folder}")
        Log.d("UtilityFragment", "Location: ${utility.location}")
        Log.d("UtilityFragment", "Link: ${utility.link}")
        Log.d("UtilityFragment", "Icon: ${utility.icon}")
        Log.d("UtilityFragment", "=========================")
        
        // Navigate to utility detail page based on FOLDER (like iOS)
        val intent = when (utility.folder.lowercase()) {
            "abt" -> android.content.Intent(context, com.sofindo.ems.activities.AbtPageActivity::class.java)
            "fuel" -> android.content.Intent(context, com.sofindo.ems.activities.FuelPageActivity::class.java)
            "kwhtm" -> android.content.Intent(context, com.sofindo.ems.activities.KwhtmpageActivity::class.java)
            "kwhtr" -> android.content.Intent(context, com.sofindo.ems.activities.KwhtrpageActivity::class.java)
            "chiller" -> android.content.Intent(context, com.sofindo.ems.activities.ChillerpageActivity::class.java)
            "freezer" -> android.content.Intent(context, com.sofindo.ems.activities.FreezerpageActivity::class.java)
            "gas" -> android.content.Intent(context, com.sofindo.ems.activities.GaspageActivity::class.java)
            "heatpump" -> android.content.Intent(context, com.sofindo.ems.activities.HeatpumppageActivity::class.java)
            "pdam" -> android.content.Intent(context, com.sofindo.ems.activities.PdampageActivity::class.java)
            "watertank" -> android.content.Intent(context, com.sofindo.ems.activities.WatertankpageActivity::class.java)
            // Fallback: try to match with codeName or utility name
            else -> {
                Log.d("UtilityFragment", "Unknown folder: '${utility.folder}', trying fallback...")
                when {
                    utility.codeName.lowercase() == "abt" || utility.utilityName.lowercase().contains("abt") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.AbtPageActivity::class.java)
                    utility.codeName.lowercase() == "fuel" || utility.utilityName.lowercase().contains("fuel") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.FuelPageActivity::class.java)
                    utility.codeName.lowercase() == "kwhtmpage" || utility.utilityName.lowercase().contains("kwh") && utility.utilityName.lowercase().contains("temp") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.KwhtmpageActivity::class.java)
                    utility.codeName.lowercase() == "kwhtrpage" || utility.utilityName.lowercase().contains("kwh") && utility.utilityName.lowercase().contains("trans") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.KwhtrpageActivity::class.java)
                    utility.codeName.lowercase() == "chillerpage" || utility.utilityName.lowercase().contains("chiller") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.ChillerpageActivity::class.java)
                    utility.codeName.lowercase() == "freezerpage" || utility.utilityName.lowercase().contains("freezer") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.FreezerpageActivity::class.java)
                    utility.codeName.lowercase() == "gaspage" || utility.utilityName.lowercase().contains("gas") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.GaspageActivity::class.java)
                    utility.codeName.lowercase() == "heatpumppage" || utility.utilityName.lowercase().contains("heat pump") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.HeatpumppageActivity::class.java)
                    utility.codeName.lowercase() == "pdampage" || utility.utilityName.lowercase().contains("pdam") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.PdampageActivity::class.java)
                    utility.codeName.lowercase() == "watertankpage" || utility.utilityName.lowercase().contains("water tank") -> 
                        android.content.Intent(context, com.sofindo.ems.activities.WatertankpageActivity::class.java)
                    else -> {
                        Toast.makeText(context, "Page for ${utility.utilityName} (folder: ${utility.folder}, codeName: ${utility.codeName}) not implemented yet", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
        }
        
        // Pass utility parameters to activity
        intent.putExtra("utility_id", utility.id)
        intent.putExtra("prop_id", utility.propID)
        intent.putExtra("code_name", utility.codeName)
        intent.putExtra("category", utility.category)
        intent.putExtra("utility_name", utility.utilityName)
        intent.putExtra("satuan", utility.satuan)
        intent.putExtra("folder", utility.folder)
        intent.putExtra("location", utility.location)
        intent.putExtra("link", utility.link)
        intent.putExtra("icon", utility.icon)
        
        // Debug logging for gas utility
        if (utility.folder.lowercase() == "gas" || utility.codeName.lowercase() == "gaspage") {
            Log.d("UtilityFragment", "Sending gas utility parameters:")
            Log.d("UtilityFragment", "propID: '${utility.propID}'")
            Log.d("UtilityFragment", "codeName: '${utility.codeName}'")
            Log.d("UtilityFragment", "utilityName: '${utility.utilityName}'")
        }
        
        startActivity(intent)
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun showEmpty() {
        tvEmpty.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }
    
    private fun showContent() {
        tvEmpty.visibility = View.GONE
        tvError.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
}

// Category Adapter
class UtilityCategoryAdapter(
    private val categories: List<String>,
    private val groupedUtilities: Map<String, List<Utility>>,
    private val onItemClick: (Utility) -> Unit
) : RecyclerView.Adapter<UtilityCategoryAdapter.CategoryViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_utility, parent, false)
        return CategoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        val utilities = groupedUtilities[category] ?: emptyList()
        holder.bind(category, utilities)
    }
    
    override fun getItemCount() = categories.size
    
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategory: TextView = itemView.findViewById(R.id.tv_category)
        private val recyclerUtilities: RecyclerView = itemView.findViewById(R.id.recycler_utilities)
        
        fun bind(category: String, utilities: List<Utility>) {
            tvCategory.text = category.uppercase()
            
            recyclerUtilities.layoutManager = GridLayoutManager(itemView.context, 4)
            recyclerUtilities.adapter = UtilityGridAdapter(utilities, onItemClick)
        }
    }
}

// Grid Adapter for Utilities
class UtilityGridAdapter(
    private val utilities: List<Utility>,
    private val onItemClick: (Utility) -> Unit
) : RecyclerView.Adapter<UtilityGridAdapter.UtilityViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UtilityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_utility_card, parent, false)
        return UtilityViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: UtilityViewHolder, position: Int) {
        holder.bind(utilities[position])
    }
    
    override fun getItemCount() = utilities.size
    
    inner class UtilityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: android.widget.ImageView = itemView.findViewById(R.id.iv_icon)
        private val tvUtilityName: TextView = itemView.findViewById(R.id.tv_utility_name)
        
        fun bind(utility: Utility) {
            tvUtilityName.text = utility.utilityName
            
            // Load icon without any background or border
            val iconUrl = "https://emshotels.net/utility/icon/${utility.icon}"
            Glide.with(itemView.context)
                .load(iconUrl)
                .placeholder(R.drawable.ic_build)
                .error(R.drawable.ic_build)
                .fitCenter()
                .into(ivIcon)
            
            // Ensure no background or border on ImageView
            ivIcon.background = null
            ivIcon.setPadding(0, 0, 0, 0)
            
            itemView.setOnClickListener {
                onItemClick(utility)
            }
        }
    }
}
