package com.example.ecotrack.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.ecotrack.R
import com.example.ecotrack.models.payment.Payment
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class PaymentOrderAdapter(
    private val payments: List<Payment>,
    private val isActive: Boolean,
    private val section: String,
    private val onViewClick: (Payment) -> Unit
) : RecyclerView.Adapter<PaymentOrderAdapter.PaymentOrderViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentOrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job_order, parent, false)
        return PaymentOrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentOrderViewHolder, position: Int) {
        val payment = payments[position]
        holder.bind(payment)
    }

    override fun getItemCount(): Int = payments.size

    inner class PaymentOrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val customerNameTextView: TextView = itemView.findViewById(R.id.tvCustomerName)
        private val addressTextView: TextView = itemView.findViewById(R.id.tvAddress)
        private val priceTextView: TextView = itemView.findViewById(R.id.tvPrice)
        private val dateTextView: TextView = itemView.findViewById(R.id.tvStatus)
        private val viewButton: Button = itemView.findViewById(R.id.btnView)
        private val cardView: CardView = itemView as CardView

        fun bind(payment: Payment) {
            customerNameTextView.text = payment.customerName
            
            // Use the address from the payment
            addressTextView.text = payment.address
            
            // Format the price with currency symbol
            val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
            priceTextView.text = formatter.format(payment.totalAmount)
            
            // Set date text based on section type
            if (section == "Completed") {
                // For completed items, show the updatedAt date or fall back to createdAt
                val dateText = if (payment.updatedAt != null) {
                    "Updated: ${dateFormat.format(payment.updatedAt)}"
                } else if (payment.createdAt != null) {
                    // Fallback to createdAt if updatedAt is null
                    "Created: ${dateFormat.format(payment.createdAt)}"
                } else {
                    "Date: N/A"
                }
                dateTextView.text = dateText
            } else if (section == "In-Progress") {
                // For in-progress items, show the status (Accepted or In-Progress)
                dateTextView.text = payment.jobOrderStatus ?: "Unknown Status"
            } else {
                // For available items, show the createdAt date
                val dateText = if (payment.createdAt != null) {
                    "Created: ${dateFormat.format(payment.createdAt)}"
                } else {
                    "Date: N/A"
                }
                dateTextView.text = dateText
            }
            
            // Apply different styling based on section
            when (section) {
                "In-Progress" -> {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.secondary))
                }
                "Available" -> {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.secondary))
                }
                "Completed" -> {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.gray))
                }
            }

            // Set click listener for view button - always allow viewing details
            viewButton.setOnClickListener {
                onViewClick(payment)
            }
        }
    }
} 