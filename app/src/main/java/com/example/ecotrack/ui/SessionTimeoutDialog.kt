package com.example.ecotrack.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.ecotrack.LoginActivity
import com.example.ecotrack.R
import com.example.ecotrack.utils.SessionManager

class SessionTimeoutDialog : DialogFragment() {
    private var listener: SessionTimeoutListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "SessionTimeoutDialog"
    
    interface SessionTimeoutListener {
        fun onLoginClicked()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "onAttach")
        if (context is SessionTimeoutListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.dialog_session_timeout, container, false)
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            Log.d(TAG, "Setting dialog window attributes")
            val width = WindowManager.LayoutParams.MATCH_PARENT
            val height = WindowManager.LayoutParams.WRAP_CONTENT
            window.setLayout(width, height)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated with view: $view")
        
        val reloginButton = view.findViewById<Button>(R.id.btnRelogin)
        Log.d(TAG, "Relogin button found: $reloginButton")
        
        reloginButton?.setOnClickListener {
            Log.d(TAG, "Relogin button clicked")
            navigateToLogin()
        }
    }
    
    private fun navigateToLogin() {
        Log.d(TAG, "Navigating to login")
        try {
            // Always logout the user regardless of listener
            val sessionManager = SessionManager.getInstance(requireContext())
            sessionManager.logout()
            
            if (listener != null) {
                listener?.onLoginClicked()
                dismissAllowingStateLoss()
                return
            }
            
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            
            // Finish the current activity
            activity?.finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to login", e)
        } finally {
            dismissAllowingStateLoss()
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "onCreateDialog")
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        return dialog
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            Log.d(TAG, "Showing dialog with tag: $tag")
            
            // First check if dialog is already showing to avoid duplicates
            val existingDialog = manager.findFragmentByTag(tag)
            if (existingDialog != null) {
                Log.d(TAG, "Dialog already exists, not showing again")
                return
            }
            
            if (manager.isDestroyed) {
                Log.e(TAG, "Cannot show dialog - FragmentManager is destroyed")
                return
            }
            
            // Use commitAllowingStateLoss to avoid state loss exceptions
            try {
                val transaction = manager.beginTransaction()
                transaction.add(this, tag)
                transaction.commitAllowingStateLoss()
            } catch (e: Exception) {
                Log.e(TAG, "Error in commit: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing dialog", e)
        }
    }

    companion object {
        const val TAG = "SessionTimeoutDialog"
        
        fun newInstance(): SessionTimeoutDialog {
            return SessionTimeoutDialog()
        }
    }
} 