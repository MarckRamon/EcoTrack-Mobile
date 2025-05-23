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
import java.util.Locale

class PaymentOrderAdapter(
    private val payments: List<Payment>,
    private val isAvailable: Boolean,
    private val onViewClick: (Payment) -> Unit
) : RecyclerView.Adapter<PaymentOrderAdapter.PaymentOrderViewHolder>() {

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
        private val viewButton: Button = itemView.findViewById(R.id.btnView)
        private val statusTextView: TextView = itemView.findViewById(R.id.tvStatus)
        private val cardView: CardView = itemView as CardView

        fun bind(payment: Payment) {
            customerNameTextView.text = payment.customerName
            
            // Use the address from the payment
            addressTextView.text = payment.address
            
            // Format the price with currency symbol
            val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
            priceTextView.text = formatter.format(payment.totalAmount)
            
            // Set status text
            statusTextView.text = payment.jobOrderStatus ?: "Available"
            
            // Apply different styling based on whether it's available or completed
            if (isAvailable) {
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.secondary))
            } else {
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.gray))
            }

            // Set click listener for view button
            viewButton.setOnClickListener {
                onViewClick(payment)
            }
        }
    }
} 