package com.example.ecotrack.ui.pickup.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecotrack.R
import com.example.ecotrack.models.Truck
import com.example.ecotrack.ui.pickup.adapters.TruckAdapter

class TruckSelectionDialog(
    context: Context,
    private val trucks: List<Truck>,
    private val onTruckSelected: (Truck) -> Unit
) : Dialog(context) {

    private lateinit var truckAdapter: TruckAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_truck_selection)
        
        // Set dialog width to match parent (90% of screen width)
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        
        // Initialize close button
        val btnClose = findViewById<ImageView>(R.id.btn_close_dialog)
        btnClose.setOnClickListener {
            dismiss()
        }
        
        // Initialize RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.rv_trucks)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Initialize adapter
        truckAdapter = TruckAdapter(trucks) { selectedTruck ->
            onTruckSelected(selectedTruck)
            dismiss()
        }
        
        recyclerView.adapter = truckAdapter
    }
} 