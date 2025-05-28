package com.example.ecotrack.ui.pickup

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ecotrack.HomeActivity
import com.example.ecotrack.R
import com.example.ecotrack.ui.pickup.model.PaymentMethod
import com.example.ecotrack.ui.pickup.model.PickupOrder
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderReceiptActivity : AppCompatActivity() {

    private lateinit var tvReceiptNumber: TextView
    private lateinit var tvPaymentType: TextView
    private lateinit var tvCustomerName: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvWasteType: TextView
    private lateinit var tvSacks: TextView
    private lateinit var tvTruck: TextView
    private lateinit var tvSacksPrice: TextView
    private lateinit var tvTruckPrice: TextView
    private lateinit var tvTax: TextView
    private lateinit var tvTotal: TextView
    private lateinit var btnBackToHome: Button
    private lateinit var btnDownloadLayout: ConstraintLayout
    private lateinit var receiptContent: ConstraintLayout
    private lateinit var order: PickupOrder
    
    // Bottom navigation
    private lateinit var navHome: LinearLayout
    private lateinit var navSchedule: LinearLayout
    private lateinit var navLocation: LinearLayout
    private lateinit var navPickup: LinearLayout
    
    companion object {
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_receipt)

        // Get order data from intent
        order = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ORDER_DATA", PickupOrder::class.java) ?: throw IllegalStateException("No order data provided")
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ORDER_DATA") ?: throw IllegalStateException("No order data provided")
        }

        // Initialize views
        tvReceiptNumber = findViewById(R.id.tv_receipt_number)
        tvPaymentType = findViewById(R.id.tv_payment_type)
        tvCustomerName = findViewById(R.id.tv_customer_name)
        tvAddress = findViewById(R.id.tv_address)
        tvEmail = findViewById(R.id.tv_email)
        tvWasteType = findViewById(R.id.tv_waste_type)
        tvSacks = findViewById(R.id.tv_sacks)
        tvTruck = findViewById(R.id.tv_truck)
        tvSacksPrice = findViewById(R.id.tv_sacks_price)
        tvTruckPrice = findViewById(R.id.tv_truck_price)
        tvTax = findViewById(R.id.tv_tax)
        tvTotal = findViewById(R.id.tv_total)
        btnBackToHome = findViewById(R.id.btn_back_to_home)
        btnDownloadLayout = findViewById(R.id.btn_download)
        receiptContent = findViewById(R.id.receipt_content)

        // Initialize bottom navigation if it exists in this layout
        try {
            navHome = findViewById(R.id.nav_home)
            navSchedule = findViewById(R.id.nav_schedule)
            navLocation = findViewById(R.id.nav_location)
            navPickup = findViewById(R.id.nav_pickup)

            // Set click listeners for bottom navigation
            navHome.setOnClickListener {
                navigateToHome()
            }
            
            navSchedule.setOnClickListener {
                navigateToHome()
            }
            
            navLocation.setOnClickListener {
                navigateToHome()
            }
            
            navPickup.setOnClickListener {
                navigateToPickup()
            }
        } catch (e: Exception) {
            // Bottom navigation might not be in this layout
        }

        // Set values
        tvReceiptNumber.text = order.referenceNumber
        tvPaymentType.text = order.paymentMethod.getDisplayName()
        tvCustomerName.text = order.fullName
        tvAddress.text = order.address
        tvEmail.text = order.email
        tvWasteType.text = order.wasteType.getDisplayName()
        tvSacks.text = order.numberOfSacks.toString()
        tvTruck.text = order.truckSize.getDisplayName()
        tvSacksPrice.text = "₱${order.sacksCost.toInt()}"
        tvTruckPrice.text = "₱${order.truckCost.toInt()}"
        tvTax.text = "₱${order.tax.toInt()}"
        tvTotal.text = "₱${order.total.toInt()}"

        // Set button click listeners
        btnBackToHome.setOnClickListener {
            finish() // Just go back to previous screen
        }
        
        btnDownloadLayout.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // For Android 9 and below, we need to request permissions
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE
                    )
                } else {
                    captureAndSaveReceiptAsImage()
                }
            } else {
                // For Android 10+, we don't need runtime permission
                captureAndSaveReceiptAsImage()
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted, save the image
                    captureAndSaveReceiptAsImage()
                } else {
                    // Permission denied
                    Toast.makeText(this, "Storage permission is required to save the receipt", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }
    
    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun navigateToPickup() {
        val intent = Intent(this, OrderPickupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }
    
    private fun captureAndSaveReceiptAsImage() {
        // Create a bitmap of the receipt content
        val bitmap = getBitmapFromView(receiptContent)
        
        // Save the bitmap to gallery
        saveBitmapToGallery(bitmap)
    }
    
    private fun getBitmapFromView(view: View): Bitmap {
        // Define a bitmap with the same size as the view
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        
        // Bind a canvas to it
        val canvas = Canvas(bitmap)
        
        // Get the view's background
        val bgDrawable = view.background
        if (bgDrawable != null) {
            // Draw the background onto the canvas
            bgDrawable.draw(canvas)
        } else {
            // If no background, fill with white
            canvas.drawColor(android.graphics.Color.WHITE)
        }
        
        // Draw the view onto the canvas
        view.draw(canvas)
        
        return bitmap
    }
    
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        // Generate a file name with timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "EcoTrack_Receipt_${timestamp}.jpg"
        
        var fos: OutputStream? = null
        var imageUri: Uri? = null
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 and above, use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EcoTrack")
                }
                
                contentResolver.also { resolver ->
                    imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                }
            } else {
                // For Android 9 and below
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/EcoTrack"
                val dir = File(imagesDir)
                if (!dir.exists()) dir.mkdirs()
                val image = File(dir, filename)
                fos = FileOutputStream(image)
                imageUri = Uri.fromFile(image)
            }
            
            // Compress the bitmap and save it to the output stream
            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                Toast.makeText(this, "Receipt saved to gallery", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save receipt: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            fos?.close()
        }
    }
} 