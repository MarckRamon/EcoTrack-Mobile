package com.example.ecotrack

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        supportActionBar?.hide()
        
        setupViews()
        loadUserData()
    }

    private fun setupViews() {
        // Back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Save button
        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            saveUserData()
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        findViewById<TextInputEditText>(R.id.firstNameInput).setText(document.getString("firstName"))
                        findViewById<TextInputEditText>(R.id.lastNameInput).setText(document.getString("lastName"))
                        findViewById<TextInputEditText>(R.id.emailInput).setText(document.getString("email"))
                    }
                }
        }
    }

    private fun saveUserData() {
        val firstName = findViewById<TextInputEditText>(R.id.firstNameInput).text.toString()
        val lastName = findViewById<TextInputEditText>(R.id.lastNameInput).text.toString()
        val email = findViewById<TextInputEditText>(R.id.emailInput).text.toString()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        currentUser?.let { user ->
            val userData = hashMapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email
            )

            firestore.collection("users").document(user.uid)
                .update(userData as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
} 