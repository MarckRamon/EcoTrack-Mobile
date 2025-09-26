package com.example.grabtrash.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.grabtrash.R
import com.example.grabtrash.models.JobOrder

class JobOrderAdapter(
    private val jobOrders: List<JobOrder>,
    private val onViewClick: (JobOrder) -> Unit
) : RecyclerView.Adapter<JobOrderAdapter.JobOrderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobOrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job_order, parent, false)
        return JobOrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobOrderViewHolder, position: Int) {
        val jobOrder = jobOrders[position]
        holder.bind(jobOrder)
    }

    override fun getItemCount(): Int = jobOrders.size

    inner class JobOrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val customerNameTextView: TextView = itemView.findViewById(R.id.tvCustomerName)
        private val addressTextView: TextView = itemView.findViewById(R.id.tvAddress)
        private val priceTextView: TextView = itemView.findViewById(R.id.tvPrice)
        private val viewButton: Button = itemView.findViewById(R.id.btnView)

        fun bind(jobOrder: JobOrder) {
            customerNameTextView.text = jobOrder.customerName
            
            // Format the address as "street, city, state zipcode"
            addressTextView.text = "${jobOrder.address} ${jobOrder.city}, ${jobOrder.state} ${jobOrder.zipCode}"
            
            // Display the price
            priceTextView.text = "₱${jobOrder.price}"

            // Set click listener for view button
            viewButton.setOnClickListener {
                onViewClick(jobOrder)
            }
        }
    }
} 