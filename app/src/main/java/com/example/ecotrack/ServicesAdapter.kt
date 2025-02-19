package com.example.ecotrack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ServicesAdapter(private val services: List<ServiceItem>) :
    RecyclerView.Adapter<ServicesAdapter.ServiceViewHolder>() {

    class ServiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.serviceIcon)
        val title: TextView = itemView.findViewById(R.id.serviceTitle)
        val description: TextView = itemView.findViewById(R.id.serviceDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.service_item, parent, false)
        return ServiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = services[position]
        holder.icon.setImageResource(service.iconResId)
        holder.title.text = service.title
        holder.description.text = service.description
    }

    override fun getItemCount(): Int = services.size
} 