package com.example.grabtrash

import android.view.View
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

class CustomInfoWindow(
    layoutResId: Int, 
    mapView: MapView
) : InfoWindow(layoutResId, mapView) {

    override fun onOpen(item: Any) {
        val marker = item as Marker
        
        // Get references to the views
        val title = mView.findViewById<android.widget.TextView>(R.id.bubble_title)
        val description = mView.findViewById<android.widget.TextView>(R.id.bubble_description)
        val subdescription = mView.findViewById<android.widget.TextView>(R.id.bubble_subdescription)
        
        // Parse the marker snippet which contains both waste type and address
        val snippetParts = marker.snippet?.split("\n")
        
        // Set the view content
        title.text = marker.title
        
        if (snippetParts != null && snippetParts.size >= 2) {
            description.text = snippetParts[0]    // Waste type
            subdescription.text = snippetParts[1] // Address
        } else {
            description.text = marker.snippet
            subdescription.visibility = View.GONE
        }
    }

    override fun onClose() {
        // Clean up any resources
    }
} 