package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.sofindo.ems.R

class SupportFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize support content
        val tvSupportContent = view.findViewById<TextView>(R.id.tv_support_content)
        tvSupportContent.text = """
            Help & Support
            
            For technical support and assistance, please contact:
            
            Email: support@emshotels.net
            Phone: +62 812-4652-3308
            
            Common Issues:
            • Login problems
            • Work order creation
            • Maintenance scheduling
            • Data synchronization
            
            We're here to help you!
        """.trimIndent()
    }
}
