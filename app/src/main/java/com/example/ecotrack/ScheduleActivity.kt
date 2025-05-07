package com.example.ecotrack

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ecotrack.databinding.ActivityScheduleBinding
import com.example.ecotrack.databinding.CalendarDayLayoutBinding
import com.example.ecotrack.databinding.DialogScheduleInfoBinding
import com.example.ecotrack.models.CollectionScheduleResponse
import com.example.ecotrack.utils.ApiService
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

class ScheduleActivity : BaseActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private lateinit var apiService: ApiService
    
    // Data to store calendar display information
    private var schedulesByDate = mapOf<LocalDate, List<CollectionScheduleResponse>>()
    private var parsedSchedules = mutableListOf<Pair<LocalDateTime, CollectionScheduleResponse>>()

    private var selectedDate: LocalDate? = null
    private val today = LocalDate.now()
    
    // Default barangay ID - would ideally come from user profile
    private var userBarangayId = "wkj4ktv"

    // --- Formatters ---
    // For displaying Month YYYY in the header
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    
    // For parsing the incoming API timestamp
    private val apiDateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm:ss a 'UTC'X", Locale.ENGLISH)
    
    // For displaying date in the dialog (e.g., 21/04/2025)
    private val dialogDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    
    // For displaying time in the dialog (e.g., 06:00 PM)
    private val dialogTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    
    // For parsing time like "09:00" from recurring schedules
    private val recurringTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        apiService = ApiService.create()
        
        setupBottomNavigationBar()
        
        // Fetch real schedule data from the API
        fetchScheduleData()

        // UI Listeners
        binding.profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        
        binding.backButton.setOnClickListener {
            finish()
        }
        
        binding.nextMonthButton.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.let {
                binding.calendarView.smoothScrollToMonth(it.yearMonth.nextMonth)
            }
        }
        binding.previousMonthButton.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.let {
                binding.calendarView.smoothScrollToMonth(it.yearMonth.previousMonth)
            }
        }
    }

    private fun setupBottomNavigationBar() {
        // Make the Schedule item visually selected
        highlightSelectedNavItem()
        
        // Set up click listeners for navigation
        binding.homeNav.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        
        // Schedule nav is current page
        
        binding.pointsNav.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        
        binding.pickupNav.setOnClickListener {
            startActivity(Intent(this, com.example.ecotrack.ui.pickup.OrderPickupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
    }
    
    private fun highlightSelectedNavItem() {
        // Optional: implement visual selection indicator for current page
        // For example by changing the icon tint of the schedule icon
    }

    private fun fetchScheduleData() {
        val token = sessionManager.getToken()
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Authentication required. Please log in.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }
        
        lifecycleScope.launch {
            try {
                // Get both scheduled and recurring pickup times
                val bearerToken = "Bearer $token"
                val scheduledResponse = apiService.getSchedulesByBarangay(userBarangayId, bearerToken)
                
                if (scheduledResponse.isSuccessful && scheduledResponse.body() != null) {
                    // Process the schedule data
                    processScheduleData(scheduledResponse.body()!!)
                    setupCalendar()
                } else {
                    Log.e("ScheduleActivity", "Failed to fetch schedules: ${scheduledResponse.code()}")
                    Toast.makeText(this@ScheduleActivity, "Failed to load schedule data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ScheduleActivity", "Error fetching schedule data: ${e.message}", e)
                Toast.makeText(this@ScheduleActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processScheduleData(schedules: List<CollectionScheduleResponse>) {
        try {
            val scheduleMap = mutableMapOf<LocalDate, MutableList<CollectionScheduleResponse>>()
            val processedSchedules = mutableListOf<Pair<LocalDateTime, CollectionScheduleResponse>>()
            
            // Get the current date for recurring schedule processing
            val now = LocalDate.now()
            val startDate = now.minusMonths(3)
            val endDate = now.plusMonths(24)  // Show schedules for next 24 months
            
            for (schedule in schedules) {
                if (!schedule.isActive) continue // Skip inactive schedules
                
                if (schedule.isRecurring && schedule.recurringDay != null && schedule.recurringTime != null) {
                    // Process recurring schedules - add them on each appropriate day
                    val dayOfWeek = try {
                        DayOfWeek.valueOf(schedule.recurringDay.uppercase())
                    } catch (e: IllegalArgumentException) {
                        Log.e("ScheduleActivity", "Invalid day of week: ${schedule.recurringDay}")
                        continue
                    }
                    
                    val timeOfDay = try {
                        LocalTime.parse(schedule.recurringTime, recurringTimeFormatter)
                    } catch (e: DateTimeParseException) {
                        Log.e("ScheduleActivity", "Invalid time format: ${schedule.recurringTime}")
                        continue
                    }
                    
                    // Generate dates for this day of week from now until endDate
                    var currDate = startDate
                    while (currDate.isBefore(endDate) || currDate.isEqual(endDate)) {
                        if (currDate.dayOfWeek == dayOfWeek) {
                            val dateTime = LocalDateTime.of(currDate, timeOfDay)
                            
                            // Add to the map of dates
                            if (!scheduleMap.containsKey(currDate)) {
                                scheduleMap[currDate] = mutableListOf()
                            }
                            scheduleMap[currDate]!!.add(schedule)
                            
                            // Also add to processed schedules for the dialog
                            processedSchedules.add(Pair(dateTime, schedule))
                        }
                        currDate = currDate.plusDays(1)
                    }
                } else if (!schedule.isRecurring && schedule.collectionDateTime != null) {
                    // Process one-time schedules
                    try {
                        val dateTime = LocalDateTime.parse(
                            schedule.collectionDateTime,
                            apiDateTimeFormatter
                        )
                        
                        val scheduleDate = dateTime.toLocalDate()
                        
                        // Add to the map of dates
                        if (!scheduleMap.containsKey(scheduleDate)) {
                            scheduleMap[scheduleDate] = mutableListOf()
                        }
                        scheduleMap[scheduleDate]!!.add(schedule)
                        
                        // Also add to processed schedules for the dialog
                        processedSchedules.add(Pair(dateTime, schedule))
                    } catch (e: DateTimeParseException) {
                        Log.e("ScheduleActivity", "Error parsing date: ${schedule.collectionDateTime}", e)
                    }
                }
            }
            
            // Update the model with the processed data
            schedulesByDate = scheduleMap
            parsedSchedules = processedSchedules
            
            Log.d("ScheduleActivity", "Successfully processed ${schedulesByDate.size} schedule dates")
        } catch (e: Exception) {
            Log.e("ScheduleActivity", "Error processing schedule data: ${e.message}", e)
            schedulesByDate = emptyMap()
            parsedSchedules = mutableListOf()
        }
    }

    private fun setupCalendar() {
        try {
            val currentMonth = YearMonth.now()
            // Define the range of months to display
            val startMonth = currentMonth.minusMonths(3)
            val endMonth = currentMonth.plusMonths(24)
            val firstDayOfWeek = firstDayOfWeekFromLocale() // Adjust if needed (e.g., DayOfWeek.SUNDAY)

            // First setup the calendar with basic configuration
            binding.calendarView.setup(startMonth, endMonth, firstDayOfWeek)
            
            // Day binder: How each day cell is created and populated
            binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View) = DayViewContainer(view)
                override fun bind(container: DayViewContainer, data: CalendarDay) {
                    // Safe initialization
                    container.day = data
                    
                    // Safely get UI elements with null checks
                    val textView = container.binding.dayText
                    val layout = container.binding.dayLayout
                    val dotBio = container.binding.dotBio
                    val dotNonBio = container.binding.dotNonBio

                    // Set the day number text
                    textView.text = data.date.dayOfMonth.toString()
                    
                    // Reset visibility
                    dotBio.visibility = View.INVISIBLE
                    dotNonBio.visibility = View.INVISIBLE

                    if (data.position == DayPosition.MonthDate) {
                        // Style for dates within the current month
                        textView.setTextColor(ContextCompat.getColor(this@ScheduleActivity, R.color.black))
                        layout.setBackgroundResource(if (selectedDate == data.date) R.drawable.calendar_selected_bg else 0)

                        // Check for schedules on this date
                        val schedulesForDate = schedulesByDate[data.date]
                        if (!schedulesForDate.isNullOrEmpty()) {
                            // Show dots based on waste types present for the day
                            val hasBio = schedulesForDate.any { it.wasteType.equals("Biodegradable", ignoreCase = true) }
                            val hasNonBio = schedulesForDate.any { 
                                it.wasteType.equals("Non-Biodegradable", ignoreCase = true) || 
                                it.wasteType.equals("Non Biodegradable", ignoreCase = true) 
                            }

                            if (hasBio) dotBio.visibility = View.VISIBLE
                            if (hasNonBio) dotNonBio.visibility = View.VISIBLE
                        }

                        // Highlight today's date
                        if (data.date == today) {
                            textView.setTextColor(ContextCompat.getColor(this@ScheduleActivity, R.color.primaryGreen))
                            if (selectedDate != data.date) { // Don't draw today background if selected
                                textView.setBackgroundResource(R.drawable.calendar_today_bg)
                            }
                        } else {
                            // Ensure non-today dates don't have the today background unless selected
                            if (selectedDate != data.date) {
                                textView.setBackgroundResource(0)
                            }
                        }
                    } else {
                        // Style for dates outside the current month (out-dates)
                        textView.setTextColor(Color.GRAY)
                        layout.background = null
                    }
                }
            }

            // Add MonthHeaderBinder implementation to fix NullPointerException
            binding.calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
                override fun create(view: View) = MonthViewContainer(view)
                override fun bind(container: MonthViewContainer, data: CalendarMonth) {
                    // No additional setup needed as we're using a static header layout
                }
            }

            // Month scroll listener to update the header text (Month YYYY)
            binding.calendarView.monthScrollListener = { calendarMonth ->
                updateMonthHeader(calendarMonth.yearMonth)
            }

            // After everything is set up, scroll to current month
            binding.calendarView.scrollToMonth(currentMonth)
            
            // Initial header update
            updateMonthHeader(currentMonth)
            
            // Notify the calendar that data has changed
            binding.calendarView.notifyCalendarChanged()
        } catch (e: Exception) {
            Log.e("ScheduleActivity", "Error setting up calendar: ${e.message}", e)
            // Show a message to the user
            Toast.makeText(this, "Error setting up calendar. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    // Updates the "Month YYYY" text view
    private fun updateMonthHeader(yearMonth: YearMonth) {
        binding.monthYearText.text = yearMonth.format(monthTitleFormatter)
    }

    // View container for each day cell in the calendar
    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val binding = CalendarDayLayoutBinding.bind(view)
        lateinit var day: CalendarDay // Holds the CalendarDay object associated with this container

        init {
            // Click listener for the day cell
            view.setOnClickListener {
                if (::day.isInitialized && day.position == DayPosition.MonthDate) { // Check if day is initialized
                    val clickedDate = day.date
                    if (selectedDate == clickedDate) {
                        // Deselect if clicking the same date again
                        selectedDate = null
                        binding.root.setBackgroundResource(0)
                    } else {
                        val oldDate = selectedDate
                        selectedDate = clickedDate
                        
                        // Update UI for the newly selected date
                        binding.root.setBackgroundResource(R.drawable.calendar_selected_bg)
                        
                        // Update UI for the previously selected date (if any)
                        oldDate?.let { 
                            this@ScheduleActivity.binding.calendarView.notifyDateChanged(it) 
                        }
                        
                        // Show details for the selected date
                        showScheduleInfoDialog(clickedDate)
                    }
                }
            }
        }
    }

    // Shows the dialog with schedule information
    private fun showScheduleInfoDialog(date: LocalDate) {
        // Find the schedules matching the selected date
        val schedulesForDate = schedulesByDate[date]
        
        if (schedulesForDate.isNullOrEmpty()) {
            Toast.makeText(this, "No schedule details found for this date.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get the first schedule for this date
        val schedule = schedulesForDate.first()
        
        // Find the corresponding datetime for this schedule
        val scheduleDateTime = parsedSchedules.find { 
            it.second.scheduleId == schedule.scheduleId && 
            it.first.toLocalDate() == date 
        }?.first ?: LocalDateTime.of(date, LocalTime.of(9, 0)) // Default to 9:00 AM if time not found
        
        val dialogBinding = DialogScheduleInfoBinding.inflate(LayoutInflater.from(this))
        val dialogView = dialogBinding.root
        val builder = AlertDialog.Builder(this, R.style.TransparentDialog)
        builder.setView(dialogView)
        val dialog = builder.create()

        // Populate dialog fields
        dialogBinding.scheduleDate.text = getString(R.string.dialog_date_format, date.format(dialogDateFormatter))
        
        val timeString = if (schedule.isRecurring) {
            try {
                val time = LocalTime.parse(schedule.recurringTime, recurringTimeFormatter)
                time.format(dialogTimeFormatter)
            } catch (e: Exception) {
                schedule.recurringTime ?: "N/A"
            }
        } else {
            scheduleDateTime.format(dialogTimeFormatter)
        }
        
        dialogBinding.scheduleTime.text = getString(R.string.dialog_pickup_time_format, timeString)
        dialogBinding.scheduleLocation.text = getString(R.string.dialog_location_format, schedule.barangayName)
        dialogBinding.scheduleWasteType.text = getString(R.string.dialog_waste_type_format, schedule.wasteType)

        dialogBinding.confirmButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // View container for month header (required to fix NullPointerException)
    inner class MonthViewContainer(view: View) : ViewContainer(view)
}
