package com.example.grabtrash.models.xendit

import com.google.gson.annotations.SerializedName

/**
 * Request model for creating an invoice
 * Documentation: https://developers.xendit.co/api-reference/#create-invoice
 */
data class CreateInvoiceRequest(
    @SerializedName("external_id")
    val externalId: String,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("invoice_duration")
    val invoiceDuration: Int = 86400, // 24 hours in seconds
    
    @SerializedName("customer")
    val customer: Customer,
    
    @SerializedName("success_redirect_url")
    val successRedirectUrl: String,
    
    @SerializedName("failure_redirect_url")
    val failureRedirectUrl: String,
    
    @SerializedName("currency")
    val currency: String = "PHP",
    
    @SerializedName("items")
    val items: List<Item>,
    
    @SerializedName("fees")
    val fees: List<Fee>? = null
)

data class Customer(
    @SerializedName("given_names")
    val givenNames: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("mobile_number")
    val mobileNumber: String? = null
)

data class Item(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("quantity")
    val quantity: Int,
    
    @SerializedName("price")
    val price: Double,
    
    @SerializedName("category")
    val category: String = "Trash Pickup Service"
)

data class Fee(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("value")
    val value: Double
)

/**
 * Response model for invoice creation
 */
data class InvoiceResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("external_id")
    val externalId: String,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("merchant_name")
    val merchantName: String,
    
    @SerializedName("merchant_profile_picture_url")
    val merchantProfilePictureUrl: String?,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("invoice_url")
    val invoiceUrl: String,
    
    @SerializedName("expiry_date")
    val expiryDate: String,
    
    @SerializedName("available_banks")
    val availableBanks: List<Bank>?,
    
    @SerializedName("available_retail_outlets")
    val availableRetailOutlets: List<RetailOutlet>?,
    
    @SerializedName("available_ewallets")
    val availableEwallets: List<Ewallet>?,
    
    @SerializedName("should_exclude_credit_card")
    val shouldExcludeCreditCard: Boolean,
    
    @SerializedName("should_send_email")
    val shouldSendEmail: Boolean,
    
    @SerializedName("created")
    val created: String,
    
    @SerializedName("updated")
    val updated: String,
    
    @SerializedName("currency")
    val currency: String
)

data class Bank(
    @SerializedName("bank_code")
    val bankCode: String,
    
    @SerializedName("collection_type")
    val collectionType: String,
    
    @SerializedName("bank_account_number")
    val bankAccountNumber: String,
    
    @SerializedName("transfer_amount")
    val transferAmount: Double,
    
    @SerializedName("bank_branch")
    val bankBranch: String,
    
    @SerializedName("account_holder_name")
    val accountHolderName: String,
    
    @SerializedName("identity_amount")
    val identityAmount: Double
)

data class RetailOutlet(
    @SerializedName("retail_outlet_name")
    val retailOutletName: String
)

data class Ewallet(
    @SerializedName("ewallet_type")
    val ewalletType: String
) 