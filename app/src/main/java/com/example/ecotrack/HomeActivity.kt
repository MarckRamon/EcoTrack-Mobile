package com.example.ecotrack

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import de.hdodenhof.circleimageview.CircleImageView
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import android.content.Intent
import android.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import android.os.CountDownTimer
import android.widget.LinearLayout
import com.example.ecotrack.ServiceItem


class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var welcomeText: TextView
    private lateinit var timeRemainingText: TextView
    private var countDownTimer: CountDownTimer? = null

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in, update UI
            updateUI(currentUser)
        } else {
            // User is signed out, handle accordingly
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        // TODO: Implement UI update based on user state
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        supportActionBar?.hide()

        initializeViews()
        setupClickListeners()
        startCountdownTimer()
        loadUserData()
    }

    private fun initializeViews() {
        welcomeText = findViewById(R.id.welcomeText)
        timeRemainingText = findViewById(R.id.timeRemaining)
    }

    private fun setupClickListeners() {
        // Notification button click
        findViewById<ImageButton>(R.id.notificationButton).setOnClickListener {
            // TODO: Handle notifications
        }

        // Profile image click
        findViewById<CircleImageView>(R.id.profileImage).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // View all click
        findViewById<TextView>(R.id.viewAll).setOnClickListener {
            // TODO: Show all reminders
        }

        // Bottom navigation clicks
        findViewById<LinearLayout>(R.id.scheduleNav).setOnClickListener {
            // TODO: Navigate to schedule
        }

        findViewById<LinearLayout>(R.id.pointsNav).setOnClickListener {
            // TODO: Navigate to points
        }

        findViewById<LinearLayout>(R.id.pickupNav).setOnClickListener {
            // TODO: Navigate to pickup
        }
    }

    private fun startCountdownTimer() {
        // Example: 23 hours countdown
        val totalTimeInMillis = 23 * 60 * 60 * 1000L

        countDownTimer = object : CountDownTimer(totalTimeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / (60 * 60 * 1000)
                val minutes = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000)
                val seconds = (millisUntilFinished % (60 * 1000)) / 1000

                timeRemainingText.text = "${hours}h ${minutes}m ${seconds}s remaining"
            }

            override fun onFinish() {
                timeRemainingText.text = "Time's up!"
            }
        }.start()
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val firstName = document.getString("firstName") ?: "User"
                        welcomeText.text = "Welcome, $firstName!"
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("HomeActivity", "Error loading user data", e)
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}