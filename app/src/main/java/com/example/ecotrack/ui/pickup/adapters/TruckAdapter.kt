package com.example.ecotrack.ui.pickup.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ecotrack.R
import com.example.ecotrack.models.Truck
import com.google.android.material.chip.Chip

class TruckAdapter(
    private var trucks: List<Truck>,
    private val onTruckSelected: (Truck) -> Unit
) : RecyclerView.Adapter<TruckAdapter.TruckViewHolder>() {

    class TruckViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivTruckIcon: ImageView = itemView.findViewById(R.id.iv_truck_icon)
        val tvTruckModel: TextView = itemView.findViewById(R.id.tv_truck_model)
        val tvTruckMake: TextView = itemView.findViewById(R.id.tv_truck_make)
        val tvTruckPrice: TextView = itemView.findViewById(R.id.tv_truck_price)
        val chipTruckSize: Chip = itemView.findViewById(R.id.chip_truck_size)
        val chipWasteType: Chip = itemView.findViewById(R.id.chip_waste_type)
        val chipStatus: Chip = itemView.findViewById(R.id.chip_status)
        val tvPlateNumber: TextView = itemView.findViewById(R.id.tv_plate_number)
        val btnSelectTruck: Button = itemView.findViewById(R.id.btn_select_truck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TruckViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_truck, parent, false)
        return TruckViewHolder(view)
    }

    override fun onBindViewHolder(holder: TruckViewHolder, position: Int) {
        val truck = trucks[position]
        
        holder.tvTruckModel.text = truck.model
        holder.tvTruckMake.text = truck.make
        holder.tvTruckPrice.text = "₱${truck.truckPrice}"
        holder.chipTruckSize.text = truck.size
        holder.chipWasteType.text = truck.wasteType
        holder.chipStatus.text = truck.status
        holder.tvPlateNumber.text = "Plate: ${truck.plateNumber}"
        
        // Set colors based on size
        when (truck.size.uppercase()) {
            "SMALL" -> holder.chipTruckSize.setChipBackgroundColorResource(R.color.material_blue)
            "MEDIUM" -> holder.chipTruckSize.setChipBackgroundColorResource(R.color.material_orange)
            "LARGE" -> holder.chipTruckSize.setChipBackgroundColorResource(R.color.material_green)
        }
        
        // Set colors based on status
        when (truck.status.uppercase()) {
            "AVAILABLE" -> holder.chipStatus.setChipBackgroundColorResource(R.color.material_green)
            else -> holder.chipStatus.setChipBackgroundColorResource(R.color.material_red)
        }
        
        holder.btnSelectTruck.setOnClickListener {
            onTruckSelected(truck)
        }
    }

    override fun getItemCount(): Int = trucks.size

    fun updateTrucks(newTrucks: List<Truck>) {
        trucks = newTrucks
        notifyDataSetChanged()
    }
} 