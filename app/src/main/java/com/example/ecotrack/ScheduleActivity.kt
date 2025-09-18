package com.example.ecotrack

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ecotrack.databinding.ActivityScheduleBinding
import com.example.ecotrack.databinding.CalendarDayLayoutBinding
import com.example.ecotrack.databinding.DialogScheduleInfoBinding
import com.example.ecotrack.models.Barangay
import com.example.ecotrack.models.CollectionScheduleResponse
import com.example.ecotrack.utils.ApiService
import com.kizitonwose.calendar.core.*
import java.time.OffsetDateTime
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import android.widget.Spinner
import android.widget.AdapterView

class ScheduleActivity : BaseActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private lateinit var apiService: ApiService

    // Data to store calendar display information
    private var schedulesByDate = mapOf<LocalDate, List<CollectionScheduleResponse>>()
    private var parsedSchedules = mutableListOf<Pair<LocalDateTime, CollectionScheduleResponse>>()

    private var selectedDate: LocalDate? = null
    private val today = LocalDate.now()

    // User's barangay information
    private var userBarangayId: String? = null
    private var userBarangayName: String? = null

    // Currently selected barangay for filtering
    private var selectedBarangayId: String? = null
    private var selectedBarangayName: String? = null

    // List of all barangays
    private var barangays: List<Barangay> = listOf()

    // Current schedules being displayed
    private var currentSchedules: List<CollectionScheduleResponse> = listOf()

    // --- Formatters ---
    // For displaying Month YYYY in the header
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    // For parsing the incoming API timestamp in ISO 8601 format (e.g., 2025-05-14T01:00:56.282Z)
    private val apiDateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

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

        // Load cached profile image immediately if available
        try {
            val cachedUrl = sessionManager.getProfileImageUrl()
            if (!cachedUrl.isNullOrBlank()) {
                com.bumptech.glide.Glide.with(this)
                    .load(cachedUrl)
                    .placeholder(R.drawable.raph)
                    .error(R.drawable.raph)
                    .into(binding.profileIcon)
            }
        } catch (_: Exception) {}

        // Load barangays for the filter dropdown
        loadBarangays()

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
        // The schedule nav is already highlighted in the XML with the correct color
        // This method is kept for consistency with other activities
    }

    private fun loadBarangays() {
        Log.d("ScheduleActivity", "Starting to load barangays")
        lifecycleScope.launch {
            try {
                // First, try to get the user's barangay from their profile
                val token = sessionManager.getToken()
                val userId = sessionManager.getUserId()

                if (token != null && userId != null) {
                    val bearerToken = "Bearer $token"
                    val profileResponse = apiService.getProfile(userId, bearerToken)

                    if (profileResponse.isSuccessful && profileResponse.body() != null) {
                        val profile = profileResponse.body()!!
                        // If the profile has a barangayId, use it
                        userBarangayId = profile.barangayId
                        userBarangayName = profile.barangayName
                        Log.d("ScheduleActivity", "User barangay: $userBarangayName (ID: $userBarangayId)")
                    } else {
                        Log.w("ScheduleActivity", "Failed to get user profile: ${profileResponse.code()}")
                    }
                }

                // Now get all barangays for the filter
                if (token == null) {
                    Log.e("ScheduleActivity", "No authentication token available")
                    Toast.makeText(this@ScheduleActivity, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Add the Bearer prefix to the token
                val authHeader = "Bearer $token"
                Log.d("ScheduleActivity", "Using auth token for barangay API: $authHeader")

                try {
                    val barangaysResponse = apiService.getAllBarangays(authHeader)

                    if (barangaysResponse.isSuccessful && barangaysResponse.body() != null) {
                        val allBarangays = barangaysResponse.body()!!
                        Log.d("ScheduleActivity", "API returned ${allBarangays.size} barangays")
                        allBarangays.forEachIndexed { index, barangay ->
                            Log.d("ScheduleActivity", "Barangay $index: ${barangay.name} (ID: ${barangay.barangayId}, Active: ${barangay.isActive})")
                        }
                        // Show all barangays for debugging (remove filter)
                        barangays = allBarangays
                        Log.d("ScheduleActivity", "Loaded ${barangays.size} barangays (no filter)")
                        Log.d("ScheduleActivity", "Barangay list: ${barangays.map { it.name }}")
                        if (barangays.isEmpty() && allBarangays.isNotEmpty()) {
                            Log.w("ScheduleActivity", "No barangays found, using all barangays")
                            barangays = allBarangays
                        }
                        // Set up the barangay filter dropdown
                        setupBarangayFilter()
                        // Now fetch schedules for the selected barangay
                        fetchScheduleData()
                    } else {
                        val errorCode = barangaysResponse.code()
                        Log.e("ScheduleActivity", "Failed to load barangays: $errorCode")
                        when (errorCode) {
                            401, 403 -> {
                                // Authentication error
                                Toast.makeText(this@ScheduleActivity,
                                    "Authentication error. Please log in again.",
                                    Toast.LENGTH_SHORT).show()
                                sessionManager.logout()
                                navigateToLogin()
                            }
                            else -> {
                                // Use test data as fallback
                                Toast.makeText(this@ScheduleActivity,
                                    "Using test data",
                                    Toast.LENGTH_SHORT).show()
                                // Load test data
                                loadTestScheduleData()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScheduleActivity", "Error fetching barangays: ${e.message}", e)
                    Toast.makeText(this@ScheduleActivity, "Using test data", Toast.LENGTH_SHORT).show()
                    // Load test data
                    loadTestScheduleData()
                }
            } catch (e: Exception) {
                Log.e("ScheduleActivity", "Error loading data: ${e.message}", e)
                // Load test data as fallback
                loadTestScheduleData()
            }
        }
    }

    private fun setupBarangayFilter() {
        Log.d("ScheduleActivity", "Setting up barangay filter with ${barangays.size} barangays")

        if (barangays.isEmpty()) {
            Log.w("ScheduleActivity", "No barangays available for dropdown")
            loadTestScheduleData()
            return
        }

        val barangayNames = barangays.map { it.name }
        Log.d("ScheduleActivity", "Barangay names for dropdown: $barangayNames")

        val barangayDropdown = binding.barangayFilterDropdown
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            barangayNames
        )
        barangayDropdown.setAdapter(adapter)
        barangayDropdown.threshold = 0

        // Set initial value if needed
        if (userBarangayName != null && barangayNames.contains(userBarangayName)) {
            barangayDropdown.setText(userBarangayName, false)
            selectedBarangayName = userBarangayName
            selectedBarangayId = userBarangayId
        } else if (barangays.isNotEmpty()) {
            barangayDropdown.setText(barangayNames[0], false)
            selectedBarangayName = barangayNames[0]
            selectedBarangayId = barangays[0].barangayId
        }

        barangayDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedBarangay = barangays[position]
            selectedBarangayId = selectedBarangay.barangayId
            selectedBarangayName = selectedBarangay.name
            Log.d("ScheduleActivity", "Selected barangay: ${selectedBarangay.name} (ID: ${selectedBarangay.barangayId})")
            fetchScheduleData()
        }
    }

    private fun fetchScheduleData() {
        val token = sessionManager.getToken()
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Authentication required. Please log in.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }

        // Get user profile to get the correct barangayId
        val userId = sessionManager.getUserId()
        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "User information not found. Please log in again.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }

        lifecycleScope.launch {
            try {
                // Use the selected barangay from the filter if available
                val bearerToken = "Bearer $token"
                val barangayIdToUse = selectedBarangayId ?: userBarangayId

                if (barangayIdToUse == null) {
                    Log.w("ScheduleActivity", "No barangay selected or found in user profile")
                    Toast.makeText(this@ScheduleActivity, "Please select a barangay", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d("ScheduleActivity", "Fetching schedules for barangay ID: $barangayIdToUse")

                // Get the schedules for the selected barangay
                val scheduledResponse = apiService.getSchedulesByBarangay(barangayIdToUse, bearerToken)

                if (scheduledResponse.isSuccessful && scheduledResponse.body() != null) {
                    // Store the current schedules
                    val allSchedules = scheduledResponse.body()!!
                    Log.d("ScheduleActivity", "API returned ${allSchedules.size} schedules for barangay ID: $barangayIdToUse")
                    allSchedules.forEachIndexed { index, sched ->
                        Log.d("ScheduleActivity", "Schedule $index: ${sched.scheduleId}, Barangay: ${sched.barangayId}, WasteType: ${sched.wasteType}, DateTime: ${sched.collectionDateTime}")
                    }

                    currentSchedules = allSchedules
                    // Process the schedule data
                    processScheduleData(currentSchedules)
                    setupCalendar()
                } else {
                    val errorCode = scheduledResponse.code()
                    Log.e("ScheduleActivity", "Failed to fetch schedules: $errorCode")

                    when (errorCode) {
                        401, 403 -> {
                            // Authentication error
                            Toast.makeText(this@ScheduleActivity,
                                "Authentication error. Please log in again.",
                                Toast.LENGTH_SHORT).show()
                            sessionManager.logout()
                            navigateToLogin()
                        }
                        else -> {
                            // Use test data as fallback
                            Toast.makeText(this@ScheduleActivity,
                                "Using test schedule data",
                                Toast.LENGTH_SHORT).show()
                            // Load test data
                            loadTestScheduleData()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ScheduleActivity", "Error fetching schedule data: ${e.message}", e)
                Toast.makeText(this@ScheduleActivity, "Using test schedule data", Toast.LENGTH_SHORT).show()
                // Load test data
                loadTestScheduleData()
            }
        }
    }

    private fun loadTestScheduleData() {
        // Create test barangays
        val testBarangaysList = listOf(
            Barangay(
                barangayId = "test-bulacao",
                name = "Bulacao",
                description = "Test Barangay Bulacao",
                isActive = true
            ),
            Barangay(
                barangayId = "test-lagtang",
                name = "Lagtang",
                description = "Test Barangay Lagtang",
                isActive = true
            ),
            Barangay(
                barangayId = "test-mohon",
                name = "Mohon",
                description = "Test Barangay Mohon",
                isActive = true
            ),
            Barangay(
                barangayId = "test-cansojong",
                name = "Cansojong",
                description = "Test Barangay Cansojong",
                isActive = true
            ),
            Barangay(
                barangayId = "test-lawaan",
                name = "Lawaan I",
                description = "Test Barangay Lawaan I",
                isActive = true
            )
        )

        // Store the test barangays
        barangays = testBarangaysList

        // Set up the barangay filter dropdown
        setupBarangayFilter()

        // Create test schedules for all barangays
        val testSchedules = mutableListOf<CollectionScheduleResponse>()

        // Create schedules for each barangay
        for (barangay in testBarangaysList) {
            // Add a recurring biodegradable schedule for Mondays
            testSchedules.add(
                CollectionScheduleResponse(
                    scheduleId = "test-bio-${barangay.name.lowercase()}",
                    barangayId = barangay.barangayId,
                    barangayName = barangay.name,
                    wasteType = "Biodegradable",
                    collectionDateTime = null,
                    isRecurring = true,
                    recurringDay = "MONDAY",
                    recurringTime = "09:00",
                    notes = "Regular biodegradable waste collection for ${barangay.name}",
                    isActive = true,
                    createdAt = "2023-01-01T00:00:00Z",
                    updatedAt = "2023-01-01T00:00:00Z"
                )
            )

            // Add a recurring non-biodegradable schedule for Thursdays
            testSchedules.add(
                CollectionScheduleResponse(
                    scheduleId = "test-nonbio-${barangay.name.lowercase()}",
                    barangayId = barangay.barangayId,
                    barangayName = barangay.name,
                    wasteType = "Non-Biodegradable",
                    collectionDateTime = null,
                    isRecurring = true,
                    recurringDay = "THURSDAY",
                    recurringTime = "14:00",
                    notes = "Regular non-biodegradable waste collection for ${barangay.name}",
                    isActive = true,
                    createdAt = "2023-01-01T00:00:00Z",
                    updatedAt = "2023-01-01T00:00:00Z"
                )
            )
        }

        // Filter schedules based on the selected barangay
        val filteredSchedules = if (selectedBarangayId != null) {
            testSchedules.filter { it.barangayId == selectedBarangayId }
        } else {
            // If no barangay is selected, use the first one
            val firstBarangay = testBarangaysList.first()
            selectedBarangayId = firstBarangay.barangayId
            selectedBarangayName = firstBarangay.name
            testSchedules.filter { it.barangayId == firstBarangay.barangayId }
        }

        Log.d("ScheduleActivity", "Filtered ${filteredSchedules.size} test schedules for barangay ID: $selectedBarangayId")

        // Store the current schedules
        currentSchedules = filteredSchedules

        // Process the test schedules
        processScheduleData(filteredSchedules)
        setupCalendar()
    }

    private fun processScheduleData(schedules: List<CollectionScheduleResponse>) {
        try {
            val scheduleMap = mutableMapOf<LocalDate, MutableList<CollectionScheduleResponse>>()
            val processedSchedules = mutableListOf<Pair<LocalDateTime, CollectionScheduleResponse>>()

            // Get the current date for recurring schedule processing
            val now = LocalDate.now()
            val startDate = now.minusMonths(3)
            val endDate = now.plusMonths(24)  // Show schedules for next 24 months

            Log.d("ScheduleActivity", "Processing ${schedules.size} schedules from $startDate to $endDate")

            for (schedule in schedules) {
                if (!schedule.isActive) {
                    Log.d("ScheduleActivity", "Skipping inactive schedule: ${schedule.scheduleId}")
                    continue // Skip inactive schedules
                }

                if (schedule.isRecurring && schedule.recurringDay != null && schedule.recurringTime != null) {
                    // Process recurring schedules - add them on each appropriate day
                    Log.d("ScheduleActivity", "Processing recurring schedule: ${schedule.scheduleId}, Day: ${schedule.recurringDay}, Time: ${schedule.recurringTime}")

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
                    var addedDates = 0
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
                            addedDates++
                        }
                        currDate = currDate.plusDays(1)
                    }
                    Log.d("ScheduleActivity", "Added recurring schedule to $addedDates dates")

                } else if (!schedule.isRecurring && schedule.collectionDateTime != null) {
                    // Process one-time schedules
                    Log.d("ScheduleActivity", "Processing one-time schedule: ${schedule.scheduleId}, DateTime: ${schedule.collectionDateTime}")

                    try {
                        // Try parsing with ISO_DATE_TIME formatter first
                        Log.d("ScheduleActivity", "Attempting to parse date: ${schedule.collectionDateTime}")
                        val dateTime = try {
                            val parsedDateTime = LocalDateTime.parse(schedule.collectionDateTime, apiDateTimeFormatter)
                            Log.d("ScheduleActivity", "Successfully parsed with ISO_DATE_TIME: $parsedDateTime")
                            parsedDateTime
                        } catch (e: DateTimeParseException) {
                            Log.d("ScheduleActivity", "ISO_DATE_TIME parsing failed, trying OffsetDateTime")
                            // If that fails, try with OffsetDateTime and convert to LocalDateTime
                            try {
                                val offsetDateTime = OffsetDateTime.parse(schedule.collectionDateTime)
                                val localDateTime = offsetDateTime.toLocalDateTime()
                                Log.d("ScheduleActivity", "Successfully parsed with OffsetDateTime: $localDateTime")
                                localDateTime
                            } catch (e2: Exception) {
                                // If all parsing attempts fail, log the error and skip this schedule
                                Log.e("ScheduleActivity", "Error parsing date after multiple attempts: ${schedule.collectionDateTime}", e2)
                                continue
                            }
                        }

                        val scheduleDate = dateTime.toLocalDate()

                        // Add to the map of dates
                        if (!scheduleMap.containsKey(scheduleDate)) {
                            scheduleMap[scheduleDate] = mutableListOf()
                        }
                        scheduleMap[scheduleDate]!!.add(schedule)

                        // Also add to processed schedules for the dialog
                        processedSchedules.add(Pair(dateTime, schedule))
                        Log.d("ScheduleActivity", "Added one-time schedule for date: $scheduleDate")
                    } catch (e: Exception) {
                        Log.e("ScheduleActivity", "Unexpected error processing schedule: ${schedule.collectionDateTime}", e)
                    }
                } else {
                    Log.w("ScheduleActivity", "Schedule ${schedule.scheduleId} has invalid data: isRecurring=${schedule.isRecurring}, recurringDay=${schedule.recurringDay}, recurringTime=${schedule.recurringTime}, collectionDateTime=${schedule.collectionDateTime}")
                }
            }

            // Update the model with the processed data
            schedulesByDate = scheduleMap
            parsedSchedules = processedSchedules

            Log.d("ScheduleActivity", "Successfully processed ${schedulesByDate.size} schedule dates with ${parsedSchedules.size} total schedule entries")

            // Log some sample dates for verification
            schedulesByDate.entries.take(5).forEach { (date, scheduleList) ->
                Log.d("ScheduleActivity", "Date $date has ${scheduleList.size} schedules: ${scheduleList.map { it.wasteType }}")
            }
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

            Log.d("ScheduleActivity", "Setting up calendar with ${schedulesByDate.size} schedule dates")

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
                            Log.d("ScheduleActivity", "Found ${schedulesForDate.size} schedules for date ${data.date}")

                            // Show dots based on waste types present for the day
                            val hasBio = schedulesForDate.any { it.wasteType.equals("Biodegradable", ignoreCase = true) }
                            val hasNonBio = schedulesForDate.any {
                                it.wasteType.equals("NON_BIODEGRADABLE", ignoreCase = true)
                            }

                            if (hasBio) {
                                dotBio.visibility = View.VISIBLE
                                Log.d("ScheduleActivity", "Showing biodegradable dot for date ${data.date}")
                            }

                            if (hasNonBio) {
                                dotNonBio.visibility = View.VISIBLE
                                Log.d("ScheduleActivity", "Showing non-biodegradable dot for date ${data.date}")
                            }
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

            // Log calendar setup completion
            Log.d("ScheduleActivity", "Calendar setup complete with ${schedulesByDate.size} dates")
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
