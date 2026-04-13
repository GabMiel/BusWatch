package com.example.buswatch.admin

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class AdminHome : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var drawerLayout: DrawerLayout
    
    private var activeUsers = mutableListOf<UserAdmin>()
    private var activeDrivers = mutableListOf<UserAdmin>()
    private var pendingUsers = mutableListOf<UserAdmin>()
    private var mapRequests = mutableListOf<MapRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_main)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        drawerLayout = findViewById(R.id.drawerLayout)
        val btnMenuToggle = findViewById<ImageButton>(R.id.btnMenuToggle)

        btnMenuToggle.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        setupSidebarNavigation()
        
        if (savedInstanceState == null) {
            loadDashboard()
        }
    }

    private fun setupSidebarNavigation() {
        findViewById<LinearLayout>(R.id.navDashboard)?.setOnClickListener {
            loadDashboard()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navUsers)?.setOnClickListener {
            loadUsers()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navApprovals)?.setOnClickListener {
            loadApprovals()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navArchive)?.setOnClickListener {
            loadArchive(ArchiveTab.PARENTS)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        findViewById<LinearLayout>(R.id.navBus)?.setOnClickListener {
            loadBus()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navRouting)?.setOnClickListener {
            loadRouting()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navStops)?.setOnClickListener {
            // TODO: Implement loadStops()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navLogout)?.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, Class.forName("com.example.buswatch.Login"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadLayout(layoutResId: Int) {
        val container = findViewById<FrameLayout>(R.id.fragmentContainer)
        container.removeAllViews()
        layoutInflater.inflate(layoutResId, container, true)
    }

    private fun loadDashboard() {
        loadLayout(R.layout.fragment_dashboard)
        updateDashboardStats()
    }

    private fun updateDashboardStats() {
        db.collection("parents").whereEqualTo("status", "approved").get()
            .addOnSuccessListener { snapshots ->
                findViewById<TextView>(R.id.tvCountParents)?.text = snapshots.size().toString()
            }

        db.collection("drivers").whereEqualTo("status", "active").get()
            .addOnSuccessListener { snapshots ->
                findViewById<TextView>(R.id.tvCountDrivers)?.text = snapshots.size().toString()
            }

        db.collection("buses").whereEqualTo("status", "Active").get()
            .addOnSuccessListener { snapshots ->
                findViewById<TextView>(R.id.tvCountBuses)?.text = snapshots.size().toString()
            }

        db.collection("parents").whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snapshots ->
                findViewById<TextView>(R.id.tvCountPending)?.text = snapshots.size().toString()
            }
    }

    private fun loadUsers() {
        loadLayout(R.layout.fragment_users)
        findViewById<TextView>(R.id.tabDrivers)?.setOnClickListener { loadDrivers() }
        fetchParents()
    }

    private fun fetchParents() {
        db.collection("parents").whereEqualTo("status", "approved").get()
            .addOnSuccessListener { documents ->
                activeUsers.clear()
                for (doc in documents) {
                    @Suppress("UNCHECKED_CAST")
                    val profile = doc.get("profile") as? kotlin.collections.Map<String, Any>
                    val fName = profile?.get("firstName") as? String ?: doc.getString("firstName") ?: ""
                    val lName = profile?.get("lastName") as? String ?: doc.getString("lastName") ?: ""
                    activeUsers.add(UserAdmin(doc.id, "$fName $lName", "Parent", status = "approved"))
                }
                setupUserList()
            }
    }

    private fun setupUserList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerUsers) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = UserAdapter(activeUsers,
            onViewClick = { user -> showParentDetail(user) { loadUsers() } },
            onEditClick = { user -> editParentDetail(user) },
            onArchiveClick = { user, _ -> archiveUser(user) }
        )
    }

    private fun loadDrivers() {
        loadLayout(R.layout.fragment_driver)
        findViewById<TextView>(R.id.tabParents)?.setOnClickListener { loadUsers() }
        findViewById<TextView>(R.id.btnAddNewDriver)?.setOnClickListener { showAddDriverDialog() }
        fetchDrivers()
    }

    private fun fetchDrivers() {
        db.collection("drivers").whereEqualTo("status", "active").get()
            .addOnSuccessListener { documents ->
                activeDrivers.clear()
                for (doc in documents) {
                    val fName = doc.getString("firstName") ?: ""
                    val lName = doc.getString("lastName") ?: ""
                    activeDrivers.add(UserAdmin(doc.id, "$fName $lName", "Driver", status = "active"))
                }
                setupDriverList()
            }
    }

    private fun setupDriverList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerUsers) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DriverAdapter(activeDrivers,
            onViewClick = { user -> showDriverDetail(user) { loadDrivers() } },
            onEditClick = { user -> editDriverDetail(user) },
            onArchiveClick = { user, _ -> archiveUser(user) }
        )
    }

    private fun archiveUser(user: UserAdmin) {
        val collection = if (user.role == "Parent") "parents" else "drivers"
        db.collection(collection).document(user.id).update("status", "archived")
            .addOnSuccessListener {
                Toast.makeText(this, getString(CommonR.string.archived_successfully), Toast.LENGTH_SHORT).show()
                if (user.role == "Parent") loadUsers()
                else loadDrivers()
            }
    }

    private fun loadBus() {
        loadLayout(R.layout.fragment_bus)
        findViewById<TextView>(R.id.btnAddNewBus)?.setOnClickListener { showAddBusDialog() }
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            val busList = mutableListOf<BusAdmin>()
            for (doc in snapshots) {
                busList.add(BusAdmin(doc.id, doc.getString("busNumber") ?: "N/A", doc.getString("status") ?: "Active"))
            }
            setupBusList(busList)
        }
    }

    private fun setupBusList(buses: List<BusAdmin>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerBuses) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = BusAdapter(buses,
            onViewClick = { bus -> showBusDetail(bus) },
            onEditClick = { bus -> editBusDetail(bus) },
            onArchiveClick = { bus -> archiveBus(bus) }
        )
    }

    private fun archiveBus(bus: BusAdmin) {
        db.collection("buses").document(bus.id).update("status", "Archived")
            .addOnSuccessListener {
                Toast.makeText(this, getString(CommonR.string.archived_successfully), Toast.LENGTH_SHORT).show()
                loadBus()
            }
    }

    private fun loadRouting() {
        loadLayout(R.layout.fragment_routing)
        findViewById<TextView>(R.id.btnAddNewRoute)?.setOnClickListener { 
            showAddRouteDialog()
        }
        
        db.collection("routes").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            val routeList = mutableListOf<RouteAdmin>()
            for (doc in snapshots) {
                routeList.add(RouteAdmin(
                    id = doc.id,
                    routeName = doc.getString("routeName") ?: "N/A",
                    busNumber = doc.getString("busNumber") ?: "N/A",
                    driverName = doc.getString("driverName") ?: "N/A",
                    currentCapacity = doc.getLong("currentCapacity")?.toInt() ?: 0,
                    maxCapacity = doc.getLong("maxCapacity")?.toInt() ?: 0,
                    status = doc.getString("status") ?: "Active"
                ))
            }
            setupRouteList(routeList)
        }
    }

    private fun setupRouteList(routes: List<RouteAdmin>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerRoutes) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RouteAdapter(routes,
            onViewClick = { route -> Toast.makeText(this, "View ${route.routeName}", Toast.LENGTH_SHORT).show() },
            onEditClick = { route -> Toast.makeText(this, "Edit ${route.routeName}", Toast.LENGTH_SHORT).show() },
            onArchiveClick = { route -> archiveRoute(route) }
        )
    }

    private fun archiveRoute(route: RouteAdmin) {
        db.collection("routes").document(route.id).update("status", "Archived")
            .addOnSuccessListener {
                Toast.makeText(this, "Route archived", Toast.LENGTH_SHORT).show()
                loadRouting()
            }
    }

    private fun showAddRouteDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_route, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        
        val etRouteName = dialogView.findViewById<EditText>(R.id.etRouteName)
        val tvSelectedBus = dialogView.findViewById<TextView>(R.id.tvSelectedBus)
        val tvSelectedDriver = dialogView.findViewById<TextView>(R.id.tvSelectedDriver)
        val tvBusCapacity = dialogView.findViewById<TextView>(R.id.tvBusCapacity)
        
        var selectedBusId: String? = null
        var selectedBusNumber: String? = null
        var selectedDriverId: String? = null
        var selectedDriverName: String? = null
        var busMaxCapacity = 0

        // Bus Dropdown
        dialogView.findViewById<FrameLayout>(R.id.btnBusDropdown).setOnClickListener {
            db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
                val buses = snapshots.documents
                val options = buses.map { it.getString("busNumber") ?: "N/A" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Select Bus")
                    .setItems(options) { _, which ->
                        val busDoc = buses[which]
                        selectedBusId = busDoc.id
                        selectedBusNumber = options[which]
                        tvSelectedBus.text = selectedBusNumber
                        tvSelectedBus.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                        
                        // Fetch capacity
                        val cap = busDoc.getString("capacity") ?: "0"
                        busMaxCapacity = cap.toIntOrNull() ?: 0
                        tvBusCapacity.text = cap
                    }.show()
            }
        }

        // Driver Dropdown
        dialogView.findViewById<FrameLayout>(R.id.btnDriverDropdown).setOnClickListener {
            db.collection("drivers").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
                val drivers = snapshots.documents
                val options = drivers.map { "${it.getString("firstName")} ${it.getString("lastName")}" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Select Driver")
                    .setItems(options) { _, which ->
                        val driverDoc = drivers[which]
                        selectedDriverId = driverDoc.id
                        selectedDriverName = options[which]
                        tvSelectedDriver.text = selectedDriverName
                        tvSelectedDriver.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                    }.show()
            }
        }

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddRoute).setOnClickListener { dialog.dismiss() }
        
        dialogView.findViewById<TextView>(R.id.btnSaveRoute).setOnClickListener {
            val routeName = etRouteName.text.toString().trim()

            if (routeName.isEmpty() || selectedBusId == null || selectedDriverId == null) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val routeData = hashMapOf(
                "routeName" to routeName,
                "busId" to selectedBusId,
                "busNumber" to selectedBusNumber,
                "driverId" to selectedDriverId,
                "driverName" to selectedDriverName,
                "maxCapacity" to busMaxCapacity,
                "currentCapacity" to 0,
                "status" to "Active",
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("routes").add(routeData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Route added successfully", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadRouting()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        dialog.show()
    }

    private fun showAddDriverDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_driver, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        
        val etFirstName = dialogView.findViewById<EditText>(R.id.etFirstName)
        val etMiddleName = dialogView.findViewById<EditText>(R.id.etMiddleName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etLastName)
        val tvSuffix = dialogView.findViewById<TextView>(R.id.tvSuffix)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val etContactNumber = dialogView.findViewById<EditText>(R.id.etContactNumber)
        val etLicense = dialogView.findViewById<EditText>(R.id.etLicense)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etConfirmPassword)
        val tvLanguage = dialogView.findViewById<TextView>(R.id.tvLanguage)
        val tvCountryCode = dialogView.findViewById<TextView>(R.id.tvCountryCode)
        
        val tvFirstNameWarning = dialogView.findViewById<TextView>(R.id.tvFirstNameWarning)
        val tvMiddleNameWarning = dialogView.findViewById<TextView>(R.id.tvMiddleNameWarning)
        val tvLastNameWarning = dialogView.findViewById<TextView>(R.id.tvLastNameWarning)
        val tvEmailWarning = dialogView.findViewById<TextView>(R.id.tvEmailWarning)
        val tvPhoneWarning = dialogView.findViewById<TextView>(R.id.tvPhoneWarning)
        val tvConfirmPasswordWarning = dialogView.findViewById<TextView>(R.id.tvConfirmPasswordWarning)
        
        val btnViewPassword = dialogView.findViewById<ImageButton>(R.id.btnViewPassword)
        val btnViewConfirmPassword = dialogView.findViewById<ImageButton>(R.id.btnViewConfirmPassword)
        
        var isPasswordVisible = false
        var isConfirmPasswordVisible = false
        var isPhoneFormatting = false
        var selectedCountryCode = "+63"
        var maxPhoneDigits = 10

        // Character limits from Signup1
        etFirstName.filters = arrayOf(InputFilter.LengthFilter(50))
        etLastName.filters = arrayOf(InputFilter.LengthFilter(50))
        etMiddleName.filters = arrayOf(InputFilter.LengthFilter(20))

        // Password visibility toggles
        btnViewPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnViewPassword.setImageResource(CommonR.drawable.ic_eye)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnViewPassword.setImageResource(CommonR.drawable.ic_eye_off)
            }
            etPassword.setSelection(etPassword.text.length)
        }

        btnViewConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnViewConfirmPassword.setImageResource(CommonR.drawable.ic_eye)
            } else {
                etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnViewConfirmPassword.setImageResource(CommonR.drawable.ic_eye_off)
            }
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }

        // Name validations from Signup1
        setupNameWatcher(etFirstName, tvFirstNameWarning)
        setupNameWatcher(etLastName, tvLastNameWarning)
        setupNameWatcher(etMiddleName, tvMiddleNameWarning)

        // Email validation
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = s?.toString() ?: ""
                tvEmailWarning.isVisible = email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches().not()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun updatePhoneFilter() {
            val spaces = when (selectedCountryCode) {
                "+63", "+1", "+61", "+64", "+353" -> 2
                "+44", "+65" -> 1
                else -> 0
            }
            etContactNumber.filters = arrayOf(InputFilter.LengthFilter(maxPhoneDigits + spaces))
        }
        updatePhoneFilter()

        // Phone formatting logic from Signup1
        etContactNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isPhoneFormatting || s == null) return
                isPhoneFormatting = true
                val digits = s.toString().replace(" ", "")
                val formatted = StringBuilder()
                
                when (selectedCountryCode) {
                    "+63", "+1", "+64" -> {
                        for (i in digits.indices) {
                            formatted.append(digits[i])
                            if ((i == 2 || i == 5) && i != digits.length - 1) formatted.append(" ")
                        }
                    }
                    "+44" -> {
                        for (i in digits.indices) {
                            formatted.append(digits[i])
                            if (i == 4 && i != digits.length - 1) formatted.append(" ")
                        }
                    }
                    "+61" -> {
                        for (i in digits.indices) {
                            formatted.append(digits[i])
                            if ((i == 2 || i == 5) && i != digits.length - 1) formatted.append(" ")
                        }
                    }
                    "+65" -> {
                        for (i in digits.indices) {
                            formatted.append(digits[i])
                            if (i == 3 && i != digits.length - 1) formatted.append(" ")
                        }
                    }
                    "+353" -> {
                        for (i in digits.indices) {
                            formatted.append(digits[i])
                            if ((i == 1 || i == 4) && i != digits.length - 1) formatted.append(" ")
                        }
                    }
                    else -> formatted.append(digits)
                }

                if (formatted.toString() != s.toString()) {
                    val selection = etContactNumber.selectionStart
                    val oldLength = s.length
                    s.replace(0, s.length, formatted.toString())
                    val newLength = formatted.length
                    val newSelection = (selection + (newLength - oldLength)).coerceIn(0, newLength)
                    etContactNumber.setSelection(newSelection)
                }
                isPhoneFormatting = false
                
                tvPhoneWarning.isVisible = digits.isNotEmpty() && digits.length != maxPhoneDigits
            }
        })

        // Country Code Selector from Signup1
        dialogView.findViewById<FrameLayout>(R.id.btnCountryCode).setOnClickListener {
            val countries = arrayOf("Philippines (+63)", "USA/Canada (+1)", "UK (+44)", "Australia (+61)", "New Zealand (+64)", "Singapore (+65)", "Ireland (+353)")
            val codes = arrayOf("+63", "+1", "+44", "+61", "+64", "+65", "+353")
            val lengths = arrayOf(10, 10, 10, 9, 10, 8, 9)
            
            AlertDialog.Builder(this)
                .setTitle("Select Country Code")
                .setItems(countries) { _, which ->
                    selectedCountryCode = codes[which]
                    maxPhoneDigits = lengths[which]
                    tvCountryCode.text = selectedCountryCode
                    etContactNumber.text.clear()
                    updatePhoneFilter()
                }
                .show()
        }

        // Dropdown for Suffix
        dialogView.findViewById<FrameLayout>(R.id.btnSuffixDropdown).setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV")
            AlertDialog.Builder(this)
                .setTitle(getString(CommonR.string.select_suffix))
                .setItems(suffixes) { _, which ->
                    tvSuffix.text = suffixes[which]
                    tvSuffix.setTextColor(Color.BLACK)
                }.show()
        }

        // Dropdown for Language
        dialogView.findViewById<FrameLayout>(R.id.btnLanguageDropdown).setOnClickListener {
            val languages = arrayOf("English", "Filipino")
            AlertDialog.Builder(this)
                .setTitle(getString(CommonR.string.select_language))
                .setItems(languages) { _, which ->
                    tvLanguage.text = languages[which]
                    tvLanguage.setTextColor(Color.BLACK)
                }.show()
        }

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddDriver).setOnClickListener { dialog.dismiss() }
        
        dialogView.findViewById<TextView>(R.id.btnSaveDriver).setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val middleName = etMiddleName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etContactNumber.text.toString().replace(" ", "")
            val license = etLicense.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            val suffixText = tvSuffix.text.toString()
            val suffix = if (suffixText == getString(CommonR.string.suffix) || suffixText == getString(CommonR.string.none)) "" else suffixText
            val language = if (tvLanguage.text.toString() == getString(CommonR.string.select_language)) "English" else tvLanguage.text.toString()

            // Basic Validation
            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || license.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(CommonR.string.fill_all_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (tvFirstNameWarning.isVisible || tvLastNameWarning.isVisible || 
                tvMiddleNameWarning.isVisible || tvEmailWarning.isVisible || 
                tvPhoneWarning.isVisible || tvConfirmPasswordWarning.isVisible) {
                Toast.makeText(this, "Please correct the errors in the form", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (phone.length != maxPhoneDigits) {
                tvPhoneWarning.isVisible = true
                Toast.makeText(this, getString(CommonR.string.phone_length_warning, maxPhoneDigits), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                tvConfirmPasswordWarning.isVisible = true
                Toast.makeText(this, getString(CommonR.string.passwords_do_not_match), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else {
                tvConfirmPasswordWarning.isVisible = false
            }

            val driverData = hashMapOf(
                "firstName" to firstName,
                "middleName" to middleName,
                "lastName" to lastName,
                "suffix" to suffix,
                "email" to email,
                "phone" to "$selectedCountryCode $phone",
                "licenseNumber" to license,
                "password" to password,
                "preferredLanguage" to language,
                "status" to "active",
                "role" to "Driver",
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("drivers").add(driverData)
                .addOnSuccessListener {
                    Toast.makeText(this, getString(CommonR.string.driver_added_success), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadDrivers()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        dialog.show()
    }

    private fun setupNameWatcher(editText: EditText, warningView: TextView) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString() ?: ""
                warningView.isVisible = input.isNotEmpty() && !input.matches(Regex("^[a-zA-Z\\s.-]*$"))
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showAddBusDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_bus, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        
        val etBusNumber = dialogView.findViewById<EditText>(R.id.etBusNumber)
        val tvSelectedVehicleType = dialogView.findViewById<TextView>(R.id.tvSelectedVehicleType)
        val etCapacity = dialogView.findViewById<EditText>(R.id.etCapacity)
        val etPlateNumber = dialogView.findViewById<EditText>(R.id.etPlateNumber)
        val tvCapacityLabel = dialogView.findViewById<TextView>(R.id.tvCapacityLabel)
        val tvCapacityWarning = dialogView.findViewById<TextView>(R.id.tvCapacityWarning)
        val btnSaveBus = dialogView.findViewById<TextView>(R.id.btnSaveBus)

        var minCap = 10
        var maxCap = 100
        var isCapacityValid = true

        val vehicleTypes = arrayOf(
            "School Bus (Standard)",
            "Mini Bus",
            "Van",
            "Multi-cab / Utility Vehicle",
            "Others"
        )

        dialogView.findViewById<FrameLayout>(R.id.btnVehicleTypeDropdown).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Vehicle Type")
                .setItems(vehicleTypes) { _, which ->
                    val selected = vehicleTypes[which]
                    tvSelectedVehicleType.text = selected
                    tvSelectedVehicleType.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                    
                    val defaultVal: String
                    when (which) {
                        0 -> { minCap = 35; maxCap = 60; defaultVal = "45" }
                        1 -> { minCap = 18; maxCap = 35; defaultVal = "25" }
                        2 -> { minCap = 10; maxCap = 18; defaultVal = "14" }
                        3 -> { minCap = 10; maxCap = 20; defaultVal = "14" }
                        else -> { minCap = 10; maxCap = 100; defaultVal = "" }
                    }
                    etCapacity.setText(defaultVal)
                    etCapacity.hint = if (which == 4) "10-100" else "$minCap-$maxCap"
                    
                    validateCapacity(etCapacity.text.toString(), minCap, maxCap, tvCapacityLabel, tvCapacityWarning) { valid ->
                        isCapacityValid = valid
                    }
                }.show()
        }

        etCapacity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateCapacity(s.toString(), minCap, maxCap, tvCapacityLabel, tvCapacityWarning) { valid ->
                    isCapacityValid = valid
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddBus).setOnClickListener { dialog.dismiss() }
        
        btnSaveBus.setOnClickListener {
            val busNumber = etBusNumber.text.toString().trim()
            val capacityStr = etCapacity.text.toString().trim()
            val vehicleType = tvSelectedVehicleType.text.toString()
            val plateNumber = etPlateNumber.text.toString().trim()

            if (busNumber.isEmpty() || capacityStr.isEmpty() || vehicleType == "Select Type") {
                Toast.makeText(this, getString(CommonR.string.fill_all_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isCapacityValid) {
                Toast.makeText(this, getString(CommonR.string.error_invalid_capacity), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val busData = hashMapOf(
                "busNumber" to busNumber,
                "vehicleType" to vehicleType,
                "capacity" to capacityStr,
                "availableSeats" to capacityStr,
                "plateNumber" to plateNumber,
                "status" to "Active",
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("buses").add(busData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Bus added successfully", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadBus()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        dialog.show()
    }

    private fun validateCapacity(
        inputStr: String,
        min: Int,
        max: Int,
        label: TextView,
        warning: TextView,
        onResult: (Boolean) -> Unit
    ) {
        val input = inputStr.toIntOrNull()
        if (input == null) {
            label.isVisible = false
            warning.isVisible = false
            onResult(false)
            return
        }

        when {
            input < min -> {
                label.isVisible = false
                warning.text = getString(CommonR.string.min_format, min)
                warning.isVisible = true
                onResult(false)
            }
            input > max -> {
                label.isVisible = false
                warning.text = getString(CommonR.string.max_format, max)
                warning.isVisible = true
                onResult(false)
            }
            else -> {
                warning.isVisible = false
                label.isVisible = true
                onResult(true)
                
                // Color logic based on the range of the specific vehicle type
                val range = (max - min).coerceAtLeast(1)
                val percentage = (input - min).toFloat() / range
                when {
                    percentage < 0.6 -> {
                        label.text = getString(CommonR.string.capacity_normal)
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    }
                    percentage < 0.85 -> {
                        label.text = getString(CommonR.string.capacity_high)
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    }
                    else -> {
                        label.text = getString(CommonR.string.capacity_risk)
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    }
                }
            }
        }
    }

    private fun loadApprovals() {
        loadLayout(R.layout.fragment_approvals)
        findViewById<ImageButton>(R.id.btnBackApprovals)?.setOnClickListener { loadDashboard() }
        findViewById<TextView>(R.id.tabMapLocations)?.setOnClickListener { loadMapApprovals() }
        fetchPendingParentsForApprovals()
    }

    private fun fetchPendingParentsForApprovals() {
        db.collection("parents").whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snapshots ->
                pendingUsers.clear()
                for (doc in snapshots) {
                    @Suppress("UNCHECKED_CAST")
                    val profile = doc.get("profile") as? kotlin.collections.Map<String, Any>
                    val fName = profile?.get("firstName") as? String ?: doc.getString("firstName") ?: ""
                    val lName = profile?.get("lastName") as? String ?: doc.getString("lastName") ?: ""
                    pendingUsers.add(UserAdmin(doc.id, "$fName $lName", "Parent", status = "pending"))
                }
                setupApprovalsList()
            }
    }

    private fun loadMapApprovals() {
        loadLayout(R.layout.fragment_map_approvals)
        findViewById<ImageButton>(R.id.btnBackApprovals)?.setOnClickListener { loadDashboard() }
        findViewById<TextView>(R.id.tabRegistration)?.setOnClickListener { loadApprovals() }
        fetchMapRequests()
    }

    private fun fetchMapRequests() {
        db.collection("map_requests").whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snapshots ->
                mapRequests.clear()
                for (doc in snapshots) {
                    mapRequests.add(MapRequest(
                        id = doc.id,
                        studentName = doc.getString("studentName") ?: "N/A",
                        parentId = doc.getString("parentId") ?: "",
                        studentId = doc.getString("studentId"),
                        currentAddress = doc.getString("currentAddress") ?: "N/A",
                        pendingAddress = doc.getString("pendingAddress") ?: "N/A",
                        currentLat = doc.getDouble("currentLat") ?: 0.0,
                        currentLng = doc.getDouble("currentLng") ?: 0.0,
                        pendingLat = doc.getDouble("pendingLat") ?: 0.0,
                        pendingLng = doc.getDouble("pendingLng") ?: 0.0,
                        docPath = doc.getString("docPath") ?: ""
                    ))
                }
                setupMapApprovalsList()
            }
    }

    private fun setupMapApprovalsList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerApprovals) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = MapRequestAdapter(mapRequests) { request, _ ->
            showMapApprovalDetail(request)
        }
    }

    private fun showMapApprovalDetail(request: MapRequest) {
        loadLayout(R.layout.fragment_map_approval_detail)
        findViewById<ImageButton>(R.id.btnBackMapDetail)?.setOnClickListener { loadMapApprovals() }
        findViewById<TextView>(R.id.tvStudentName).text = request.studentName
        findViewById<TextView>(R.id.tvCurrentAddress).text = request.currentAddress
        findViewById<TextView>(R.id.tvPendingAddress).text = request.pendingAddress
        
        // Fetch and show parent info
        db.collection("parents").document(request.parentId).get().addOnSuccessListener { doc ->
            if (doc != null && doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val profile = doc.get("profile") as? kotlin.collections.Map<String, Any>
                val firstName = profile?.get("firstName") as? String ?: doc.getString("firstName") ?: ""
                val lastName = profile?.get("lastName") as? String ?: doc.getString("lastName") ?: ""
                findViewById<TextView>(R.id.tvParentFullName).text = getString(CommonR.string.name_format, firstName, lastName)
                findViewById<TextView>(R.id.tvParentPhone).text = profile?.get("phone") as? String ?: doc.getString("phone") ?: ""
                val avatar = profile?.get("avatarUrl") as? String ?: doc.getString("avatarUrl") ?: ""
                if (avatar.isNotEmpty()) Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(findViewById(R.id.imgParent))
            }
        }

        val mapCurrent = findViewById<org.osmdroid.views.MapView>(R.id.mapCurrentLocation)
        val mapPending = findViewById<org.osmdroid.views.MapView>(R.id.mapPendingLocation)

        fun setupMap(map: org.osmdroid.views.MapView, lat: Double, lng: Double) {
            map.setMultiTouchControls(true)
            map.controller.setZoom(17.0)
            val point = GeoPoint(lat, lng)
            map.controller.setCenter(point)
            val marker = Marker(map)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(marker)
        }

        setupMap(mapCurrent, request.currentLat, request.currentLng)
        setupMap(mapPending, request.pendingLat, request.pendingLng)
        
        findViewById<Button>(R.id.btnApproveMap).setOnClickListener {
            val updates = hashMapOf<String, Any>(
                "homeAddress" to request.pendingAddress,
                "address" to request.pendingAddress,
                "latitude" to request.pendingLat,
                "longitude" to request.pendingLng,
                "pendingAddress" to com.google.firebase.firestore.FieldValue.delete(),
                "pendingLatitude" to com.google.firebase.firestore.FieldValue.delete(),
                "pendingLongitude" to com.google.firebase.firestore.FieldValue.delete(),
                "addressChangeStatus" to "approved"
            )
            
            db.document(request.docPath).update(updates).addOnSuccessListener {
                db.collection("map_requests").document(request.id).update("status", "approved")
                    .addOnSuccessListener { 
                        sendNotification(request.parentId, "Location Update Approved", "The home location for ${request.studentName} has been approved.")
                        Toast.makeText(this, "Location update approved", Toast.LENGTH_SHORT).show()
                        loadMapApprovals() 
                    }
            }
        }
        
        findViewById<Button>(R.id.btnRejectMap).setOnClickListener {
            val updates = hashMapOf<String, Any>(
                "pendingAddress" to com.google.firebase.firestore.FieldValue.delete(),
                "pendingLatitude" to com.google.firebase.firestore.FieldValue.delete(),
                "pendingLongitude" to com.google.firebase.firestore.FieldValue.delete(),
                "addressChangeStatus" to "rejected"
            )
            db.document(request.docPath).update(updates).addOnSuccessListener {
                db.collection("map_requests").document(request.id).update("status", "rejected")
                    .addOnSuccessListener { 
                        sendNotification(request.parentId, "Location Update Rejected", "The home location change for ${request.studentName} was not approved.")
                        Toast.makeText(this, "Location update rejected", Toast.LENGTH_SHORT).show()
                        loadMapApprovals() 
                    }
            }
        }
    }

    private fun showParentApprovalDetail(user: UserAdmin) {
        loadLayout(R.layout.fragment_parent_approval_detail)
        findViewById<ImageButton>(R.id.btnBackParentApproval)?.setOnClickListener { loadApprovals() }
        
        db.collection("parents").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            @Suppress("UNCHECKED_CAST")
            val profile = doc.get("profile") as? kotlin.collections.Map<String, Any>
            
            val firstName = profile?.get("firstName") as? String ?: doc.getString("firstName") ?: ""
            val lastName = profile?.get("lastName") as? String ?: doc.getString("lastName") ?: ""
            val middleName = profile?.get("middleName") as? String ?: doc.getString("middleName") ?: ""
            val suffix = profile?.get("suffix") as? String ?: doc.getString("suffix") ?: ""
            val email = profile?.get("email") as? String ?: doc.getString("email") ?: ""
            val phone = profile?.get("phone") as? String ?: doc.getString("phone") ?: ""
            
            findViewById<TextView>(R.id.tvFirstName).text = firstName
            findViewById<TextView>(R.id.tvLastName).text = lastName
            findViewById<TextView>(R.id.tvMiddleName).text = middleName.ifEmpty { getString(CommonR.string.placeholder_hyphen) }
            findViewById<TextView>(R.id.tvSuffix).text = suffix.ifEmpty { getString(CommonR.string.none) }
            findViewById<TextView>(R.id.tvEmail).text = email
            findViewById<TextView>(R.id.tvPhone).text = phone
            
            val avatar = profile?.get("avatarUrl") as? String ?: doc.getString("avatarUrl") ?: ""
            if (avatar.isNotEmpty()) Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(findViewById(R.id.imgParent))
            
            // Fetch Emergency Contact from array if exists
            @Suppress("UNCHECKED_CAST")
            val emergencyContacts = doc.get("emergencyContacts") as? List<kotlin.collections.Map<String, Any>>
            if (emergencyContacts != null && emergencyContacts.isNotEmpty()) {
                val contact = emergencyContacts[0]
                findViewById<TextView>(R.id.tvEmergencyName).text = contact["name"] as? String ?: getString(CommonR.string.placeholder_hyphen)
                findViewById<TextView>(R.id.tvEmergencyPhone).text = contact["phone"] as? String ?: getString(CommonR.string.placeholder_hyphen)
                findViewById<TextView>(R.id.tvRelationship).text = contact["relationship"] as? String ?: getString(CommonR.string.placeholder_hyphen)
            }

            // Fetch Student Details
            doc.reference.collection("students").get().addOnSuccessListener { studentDocs ->
                if (studentDocs.any()) {
                    val child = studentDocs.first()
                    val childFirstName = child.getString("firstName") ?: ""
                    val childLastName = child.getString("lastName") ?: ""
                    findViewById<TextView>(R.id.tvChildFullName).text = getString(CommonR.string.name_format, childFirstName, childLastName)
                    findViewById<TextView>(R.id.tvChildAge).text = getString(CommonR.string.age_years_format, child.get("age")?.toString() ?: "-")
                    findViewById<TextView>(R.id.tvChildGrade).text = child.getString("grade") ?: getString(CommonR.string.placeholder_hyphen)
                    findViewById<TextView>(R.id.tvHomeAddress).text = child.getString("address") ?: child.getString("homeAddress") ?: getString(CommonR.string.placeholder_hyphen)
                    
                    @Suppress("UNCHECKED_CAST")
                    val medical = child.get("medical") as? kotlin.collections.Map<String, Any>
                    findViewById<TextView>(R.id.tvBloodType).text = medical?.get("bloodType") as? String ?: getString(CommonR.string.placeholder_hyphen)
                    findViewById<TextView>(R.id.tvAllergies).text = medical?.get("allergies") as? String ?: getString(CommonR.string.placeholder_hyphen)
                    findViewById<TextView>(R.id.tvConditions).text = medical?.get("conditions") as? String ?: getString(CommonR.string.placeholder_hyphen)
                    findViewById<TextView>(R.id.tvMedications).text = medical?.get("medications") as? String ?: getString(CommonR.string.placeholder_hyphen)
                    
                    val childAvatar = child.getString("avatarUrl") ?: ""
                    if (childAvatar.isNotEmpty()) Glide.with(this).load(childAvatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(findViewById(R.id.imgChild))
                }
            }
        }

        findViewById<Button>(R.id.btnApproveParent).setOnClickListener { approveUserFromApprovals(user) }
        findViewById<Button>(R.id.btnRejectParent).setOnClickListener { rejectUserFromApprovals(user) }
    }

    private fun approveUserFromApprovals(user: UserAdmin) {
        db.collection("parents").document(user.id).update("status", "approved")
            .addOnSuccessListener { 
                sendNotification(user.id, "Account Approved", "Your registration has been approved! You can now use all features of BusWatch.")
                Toast.makeText(this, getString(CommonR.string.parent_approved), Toast.LENGTH_SHORT).show()
                loadApprovals() 
            }
    }

    private fun rejectUserFromApprovals(user: UserAdmin) {
        db.collection("parents").document(user.id).update("status", "rejected")
            .addOnSuccessListener { 
                sendNotification(user.id, "Account Rejected", "Your registration request was not approved. Please contact the administrator.")
                Toast.makeText(this, getString(CommonR.string.parent_rejected), Toast.LENGTH_SHORT).show()
                loadApprovals() 
            }
    }

    private fun setupApprovalsList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerApprovals) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = PendingUserAdapter(pendingUsers) { user ->
            showParentApprovalDetail(user)
        }
    }

    private fun sendNotification(parentId: String, title: String, message: String) {
        val notification = hashMapOf(
            "title" to title,
            "message" to message,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "isRead" to false,
            "type" to "admin_update"
        )
        db.collection("parents").document(parentId).collection("notifications").add(notification)
    }

    private fun showParentDetail(user: UserAdmin, returnAction: () -> Unit) {
        loadLayout(R.layout.fragment_view_parent)
        findViewById<ImageButton>(R.id.btnBackParentDetail)?.setOnClickListener { returnAction() }
        
        findViewById<ImageButton>(R.id.btnEditParent)?.setOnClickListener {
            editParentDetail(user)
        }
        
        db.collection("parents").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            @Suppress("UNCHECKED_CAST")
            val profile = doc.get("profile") as? kotlin.collections.Map<String, Any>
            val firstName = profile?.get("firstName") as? String ?: doc.getString("firstName") ?: ""
            val lastName = profile?.get("lastName") as? String ?: doc.getString("lastName") ?: ""
            val middleName = profile?.get("middleName") as? String ?: doc.getString("middleName") ?: ""
            val suffix = profile?.get("suffix") as? String ?: doc.getString("suffix") ?: ""
            findViewById<TextView>(R.id.tvFirstName).text = firstName
            findViewById<TextView>(R.id.tvLastName).text = lastName
            findViewById<TextView>(R.id.tvMiddleName).text = middleName.ifEmpty { getString(CommonR.string.not_applicable) }
            findViewById<TextView>(R.id.tvSuffix).text = suffix.ifEmpty { getString(CommonR.string.none) }
            findViewById<TextView>(R.id.tvEmail).text = profile?.get("email") as? String ?: doc.getString("email") ?: ""
            findViewById<TextView>(R.id.tvPhone).text = profile?.get("phone") as? String ?: doc.getString("phone") ?: ""
            val avatar = profile?.get("avatarUrl") as? String ?: doc.getString("avatarUrl") ?: ""
            if (avatar.isNotEmpty()) Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(findViewById(R.id.imgParent))
            
            if (user.role == "Parent") {
                doc.reference.collection("students").get().addOnSuccessListener { studentDocs ->
                    val students = studentDocs.map { it.data + ("id" to it.id) }
                    if (students.isNotEmpty()) setupChildNavigation(students)
                }
                @Suppress("UNCHECKED_CAST")
                val contacts = doc.get("emergencyContacts") as? List<kotlin.collections.Map<String, Any>>
                if (contacts != null && contacts.isNotEmpty()) {
                    val c1 = contacts[0]
                    findViewById<TextView>(R.id.tvEmergencyName).text = c1["name"] as? String ?: getString(CommonR.string.not_applicable)
                    findViewById<TextView>(R.id.tvRelationship).text = c1["relationship"] as? String ?: getString(CommonR.string.not_applicable)
                    findViewById<TextView>(R.id.tvEmergencyPhone).text = c1["phone"] as? String ?: getString(CommonR.string.not_applicable)
                }
            }
        }
        
        findViewById<LinearLayout>(R.id.layoutActionButtons).isVisible = user.status == "pending"
        findViewById<Button>(R.id.btnAccept).setOnClickListener { approveUserFromApprovals(user) }
        findViewById<Button>(R.id.btnReject).setOnClickListener { rejectUserFromApprovals(user) }
    }

    private fun setupChildNavigation(students: List<kotlin.collections.Map<String, Any>>) {
        var index = 0
        fun displayChild(i: Int) {
            val child = students[i]
            findViewById<TextView>(R.id.tvChildFirstName).text = child["firstName"] as? String ?: ""
            findViewById<TextView>(R.id.tvChildLastName).text = child["lastName"] as? String ?: ""
            findViewById<TextView>(R.id.tvChildGrade).text = child["grade"] as? String ?: getString(CommonR.string.not_applicable)
            findViewById<TextView>(R.id.tvChildCounter).text = getString(CommonR.string.child_counter_format, i + 1, students.size)
            findViewById<TextView>(R.id.tvHomeAddress).text = child["address"] as? String ?: child["homeAddress"] as? String ?: getString(CommonR.string.not_applicable)
            
            @Suppress("UNCHECKED_CAST")
            val medical = child["medical"] as? kotlin.collections.Map<String, Any>
            findViewById<TextView>(R.id.tvBloodType).text = medical?.get("bloodType") as? String ?: getString(CommonR.string.placeholder_hyphen)
            findViewById<TextView>(R.id.tvAllergies).text = medical?.get("allergies") as? String ?: getString(CommonR.string.placeholder_hyphen)
            findViewById<TextView>(R.id.tvConditions).text = medical?.get("conditions") as? String ?: getString(CommonR.string.placeholder_hyphen)
            findViewById<TextView>(R.id.tvMedications).text = medical?.get("medications") as? String ?: getString(CommonR.string.placeholder_hyphen)

            val avatar = child["avatarUrl"] as? String ?: ""
            if (avatar.isNotEmpty()) Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(findViewById(R.id.imgChild))
        }
        displayChild(index)
        findViewById<LinearLayout>(R.id.layoutChildNavigation).isVisible = students.size > 1
        findViewById<ImageButton>(R.id.btnPrevChild).setOnClickListener { if (index > 0) displayChild(--index) }
        findViewById<ImageButton>(R.id.btnNextChild).setOnClickListener { if (index < students.size - 1) displayChild(++index) }
    }

    private fun showDriverDetail(user: UserAdmin, returnAction: () -> Unit) {
        loadLayout(R.layout.fragment_view_driver)
        findViewById<ImageButton>(R.id.btnBackDriverDetail)?.setOnClickListener { returnAction() }
        db.collection("drivers").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            findViewById<TextView>(R.id.tvFirstName).text = doc.getString("firstName") ?: ""
            findViewById<TextView>(R.id.tvLastName).text = doc.getString("lastName") ?: ""
            findViewById<TextView>(R.id.tvEmail).text = doc.getString("email") ?: ""
            findViewById<TextView>(R.id.tvPhone).text = doc.getString("phone") ?: ""
            findViewById<TextView>(R.id.tvLicenseNumber).text = doc.getString("licenseNumber") ?: ""
            findViewById<TextView>(R.id.tvMiddleName).text = doc.getString("middleName") ?: ""
            findViewById<TextView>(R.id.tvSuffix).text = doc.getString("suffix") ?: ""
            val avatar = doc.getString("driverAvatar") ?: ""
            if (avatar.isNotEmpty()) Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(findViewById(R.id.imgDriver))
        }
    }

    private fun showBusDetail(bus: BusAdmin) {
        loadLayout(R.layout.fragment_view_bus)
        findViewById<ImageButton>(R.id.btnBackBusDetail)?.setOnClickListener { loadBus() }
        db.collection("buses").document(bus.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            findViewById<TextView>(R.id.tvBusNumber).text = doc.getString("busNumber") ?: getString(CommonR.string.not_applicable)
            findViewById<TextView>(R.id.tvBusStatus).text = doc.getString("status") ?: getString(CommonR.string.active)
            findViewById<TextView>(R.id.tvCapacity).text = doc.getString("capacity") ?: getString(CommonR.string.not_applicable)
            findViewById<TextView>(R.id.tvPlateNumber).text = doc.getString("plateNumber") ?: getString(CommonR.string.not_applicable)
            doc.getString("driverId")?.let { dId ->
                db.collection("drivers").document(dId).get().addOnSuccessListener { dDoc ->
                    if (dDoc.exists()) {
                        val fullName = getString(CommonR.string.name_format, dDoc.getString("firstName"), dDoc.getString("lastName"))
                        findViewById<TextView>(R.id.tvDriverName).text = fullName
                        findViewById<TextView>(R.id.tvDriverPhone).text = dDoc.getString("phone") ?: getString(CommonR.string.not_applicable)
                    }
                }
            }
        }
    }

    private fun editParentDetail(user: UserAdmin) {
        loadLayout(R.layout.fragment_edit_parent)
        findViewById<ImageButton>(R.id.btnBackEditParent)?.setOnClickListener { showParentDetail(user) { loadUsers() } }
        
        val childrenContainer = findViewById<LinearLayout>(R.id.layoutChildrenContainer)

        db.collection("parents").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            @Suppress("UNCHECKED_CAST")
            val profile = doc.get("profile") as? kotlin.collections.Map<String, Any>
            findViewById<EditText>(R.id.etFirstName).setText(profile?.get("firstName") as? String ?: doc.getString("firstName") ?: "")
            findViewById<EditText>(R.id.etLastName).setText(profile?.get("lastName") as? String ?: doc.getString("lastName") ?: "")
            findViewById<EditText>(R.id.etMiddleName).setText(profile?.get("middleName") as? String ?: doc.getString("middleName") ?: "")
            findViewById<TextView>(R.id.tvSuffix).text = (profile?.get("suffix") as? String ?: doc.getString("suffix") ?: "").ifEmpty { getString(CommonR.string.none) }
            findViewById<EditText>(R.id.etPhone).setText(profile?.get("phone") as? String ?: doc.getString("phone") ?: "")
            val avatar = profile?.get("avatarUrl") as? String ?: doc.getString("avatarUrl") ?: ""
            if (avatar.isNotEmpty()) Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(findViewById(R.id.imgParent))
            
            doc.reference.collection("students").get().addOnSuccessListener { studentDocs ->
                childrenContainer?.removeAllViews()
                for (studentDoc in studentDocs) {
                    val childData = studentDoc.data
                    val itemView = layoutInflater.inflate(R.layout.layout_edit_child_item, childrenContainer, false)
                    
                    val headerName = itemView.findViewById<TextView>(R.id.tvChildHeaderName)
                    val firstName = childData["firstName"] as? String ?: ""
                    val lastName = childData["lastName"] as? String ?: ""
                    headerName.text = getString(CommonR.string.name_format, firstName, lastName)
                    
                    val content = itemView.findViewById<LinearLayout>(R.id.layoutChildContent)
                    val chevron = itemView.findViewById<ImageView>(R.id.ivChildChevron)
                    val toggle = itemView.findViewById<LinearLayout>(R.id.btnToggleChildInfo)
                    
                    toggle.setOnClickListener {
                        if (content.visibility == View.VISIBLE) {
                            content.visibility = View.GONE
                            chevron.rotation = -90f
                        } else {
                            content.visibility = View.VISIBLE
                            chevron.rotation = 0f
                        }
                    }
                    
                    itemView.findViewById<EditText>(R.id.etChildFirstName).setText(firstName)
                    itemView.findViewById<EditText>(R.id.etChildLastName).setText(lastName)
                    itemView.findViewById<EditText>(R.id.etChildMiddleName).setText(childData["middleName"] as? String ?: "")
                    itemView.findViewById<EditText>(R.id.etChildClass).setText(childData["class"]?.toString() ?: "")
                    itemView.findViewById<EditText>(R.id.etChildSchool).setText(childData["school"] as? String ?: "")
                    itemView.findViewById<EditText>(R.id.etChildAddress).setText(childData["address"] as? String ?: childData["homeAddress"] as? String ?: "")
                    
                    childrenContainer?.addView(itemView)
                }
            }

            findViewById<View>(R.id.btnSaveParentChanges).setOnClickListener { 
                Log.d("AdminHome", "Saving changes for parent: ${user.id}")
                Toast.makeText(this, getString(CommonR.string.changes_saved), Toast.LENGTH_SHORT).show()
                showParentDetail(user) { loadUsers() }
            }
            findViewById<View>(R.id.btnCancelParentChanges).setOnClickListener { showParentDetail(user) { loadUsers() } }
        }
    }

    private fun editDriverDetail(user: UserAdmin) {
        loadLayout(R.layout.fragment_edit_driver)
        findViewById<ImageButton>(R.id.btnBackEditDriver)?.setOnClickListener { loadDrivers() }
        db.collection("drivers").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            findViewById<EditText>(R.id.etFirstName).setText(doc.getString("firstName") ?: "")
            findViewById<EditText>(R.id.etMiddleName).setText(doc.getString("middleName") ?: "")
            findViewById<EditText>(R.id.etLastName).setText(doc.getString("lastName") ?: "")
            findViewById<TextView>(R.id.tvSuffix).text = doc.getString("suffix") ?: getString(CommonR.string.suffix)
            findViewById<EditText>(R.id.etEmail).setText(doc.getString("email") ?: "")
            findViewById<EditText>(R.id.etPhone).setText(doc.getString("phone") ?: "")
            findViewById<EditText>(R.id.etLicenseNumber).setText(doc.getString("licenseNumber") ?: "")
            val avatar = doc.getString("driverAvatar") ?: ""
            if (avatar.isNotEmpty()) Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(findViewById(R.id.imgDriver))
            findViewById<Button>(R.id.btnSaveDriverChanges).setOnClickListener { 
                Toast.makeText(this, getString(CommonR.string.details_updated), Toast.LENGTH_SHORT).show()
                loadDrivers()
            }
        }
    }

    private fun editBusDetail(bus: BusAdmin) {
        loadLayout(R.layout.fragment_edit_bus)
        findViewById<ImageButton>(R.id.btnBackEditBus)?.setOnClickListener { loadBus() }
        db.collection("buses").document(bus.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            findViewById<TextView>(R.id.tvBusNumberValue).text = doc.getString("busNumber") ?: ""
            findViewById<EditText>(R.id.etCapacity).setText(doc.getString("capacity") ?: "")
            findViewById<TextView>(R.id.tvPlateNumberValue).text = doc.getString("plateNumber") ?: ""
            findViewById<View>(R.id.btnSaveBusChanges).setOnClickListener {
                val newCapacity = findViewById<EditText>(R.id.etCapacity).text.toString().trim()
                if (newCapacity.isNotEmpty()) {
                    db.collection("buses").document(bus.id).update("capacity", newCapacity)
                        .addOnSuccessListener {
                            Toast.makeText(this, getString(CommonR.string.details_updated), Toast.LENGTH_SHORT).show()
                            loadBus()
                        }
                } else {
                    loadBus()
                }
            }
        }
    }

    private fun loadArchive(tab: ArchiveTab) {
        val layoutRes = when (tab) {
            ArchiveTab.PARENTS -> R.layout.fragment_archive
            ArchiveTab.DRIVERS -> R.layout.fragment_driver_archive
            ArchiveTab.BUS -> R.layout.fragment_bus_archive
        }
        loadLayout(layoutRes)
        findViewById<TextView>(R.id.tabArchivedParents)?.setOnClickListener { loadArchive(ArchiveTab.PARENTS) }
        findViewById<TextView>(R.id.tabArchivedDrivers)?.setOnClickListener { loadArchive(ArchiveTab.DRIVERS) }
        findViewById<TextView>(R.id.tabArchivedBus)?.setOnClickListener { loadArchive(ArchiveTab.BUS) }

        when (tab) {
            ArchiveTab.PARENTS -> fetchArchivedParents()
            ArchiveTab.DRIVERS -> fetchArchivedDrivers()
            ArchiveTab.BUS -> fetchArchivedBuses()
        }
    }

    private fun fetchArchivedParents() {
        db.collection("parents").whereIn("status", listOf("archived", "rejected")).get()
            .addOnSuccessListener { documents ->
                val list = mutableListOf<UserAdmin>()
                for (doc in documents) {
                    @Suppress("UNCHECKED_CAST")
                    val profile = doc.get("profile") as? kotlin.collections.Map<String, Any>
                    val fName = profile?.get("firstName") as? String ?: doc.getString("firstName") ?: ""
                    val lName = profile?.get("lastName") as? String ?: doc.getString("lastName") ?: ""
                    list.add(UserAdmin(doc.id, "$fName $lName", "Parent", isArchived = true, status = doc.getString("status") ?: "archived"))
                }
                
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerArchived) ?: return@addOnSuccessListener
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = ArchivedUserAdapter(list,
                    onRestoreClick = { user -> showRestoreConfirmation(user) },
                    onDeleteClick = { user -> showDeleteConfirmation(user) },
                    onViewClick = { user -> showParentDetail(user) { loadArchive(ArchiveTab.PARENTS) } }
                )
            }
    }

    private fun fetchArchivedDrivers() {
        db.collection("drivers").whereEqualTo("status", "archived").get()
            .addOnSuccessListener { documents ->
                val list = mutableListOf<UserAdmin>()
                for (doc in documents) {
                    val fName = doc.getString("firstName") ?: ""
                    val lName = doc.getString("lastName") ?: ""
                    list.add(UserAdmin(doc.id, "$fName $lName", "Driver", isArchived = true, status = "archived"))
                }
                
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerArchived) ?: return@addOnSuccessListener
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = ArchivedUserAdapter(list,
                    onRestoreClick = { user -> showRestoreConfirmation(user) },
                    onDeleteClick = { user -> showDeleteConfirmation(user) },
                    onViewClick = { user -> showDriverDetail(user) { loadArchive(ArchiveTab.DRIVERS) } }
                )
            }
    }

    private fun fetchArchivedBuses() {
        db.collection("buses").whereEqualTo("status", "Archived").get()
            .addOnSuccessListener { snapshots ->
                val list = mutableListOf<BusAdmin>()
                for (doc in snapshots) {
                    list.add(BusAdmin(doc.id, doc.getString("busNumber") ?: "N/A", "Archived"))
                }
                
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerArchived) ?: return@addOnSuccessListener
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = ArchivedBusAdapter(list,
                    onRestoreClick = { bus -> showRestoreBusConfirmation(bus) },
                    onDeleteClick = { bus -> showDeleteBusConfirmation(bus) },
                    onViewClick = { bus -> showBusDetail(bus) }
                )
            }
    }

    private fun showRestoreConfirmation(user: UserAdmin) {
        AlertDialog.Builder(this)
            .setTitle(CommonR.string.restore_warning_title)
            .setMessage(CommonR.string.restore_warning_message)
            .setPositiveButton(CommonR.string.confirm_caps) { _, _ ->
                if (user.role == "Parent") restoreUser(user)
                else restoreDriver(user)
            }
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .show()
    }

    private fun showDeleteConfirmation(user: UserAdmin) {
        AlertDialog.Builder(this)
            .setTitle(CommonR.string.delete_warning_title)
            .setMessage(CommonR.string.delete_warning_message)
            .setPositiveButton(CommonR.string.delete) { _, _ ->
                permanentDeleteUser(user)
            }
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .show()
    }

    private fun showRestoreBusConfirmation(bus: BusAdmin) {
        AlertDialog.Builder(this)
            .setTitle(CommonR.string.restore_warning_title)
            .setMessage(CommonR.string.restore_warning_message)
            .setPositiveButton(CommonR.string.confirm_caps) { _, _ ->
                restoreBus(bus)
            }
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .show()
    }

    private fun showDeleteBusConfirmation(bus: BusAdmin) {
        AlertDialog.Builder(this)
            .setTitle(CommonR.string.delete_warning_title)
            .setMessage(CommonR.string.delete_warning_message)
            .setPositiveButton(CommonR.string.delete) { _, _ ->
                permanentDeleteBus(bus)
            }
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .show()
    }

    private fun permanentDeleteUser(user: UserAdmin) {
        val collection = if (user.role == "Parent") "parents" else "drivers"
        db.collection(collection).document(user.id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Deleted permanently", Toast.LENGTH_SHORT).show()
                loadArchive(if (user.role == "Parent") ArchiveTab.PARENTS else ArchiveTab.DRIVERS)
            }
    }

    private fun permanentDeleteBus(bus: BusAdmin) {
        db.collection("buses").document(bus.id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Bus deleted permanently", Toast.LENGTH_SHORT).show()
                loadArchive(ArchiveTab.BUS)
            }
    }

    private fun restoreUser(user: UserAdmin) {
        db.collection("parents").document(user.id).update("status", "approved")
            .addOnSuccessListener { loadArchive(ArchiveTab.PARENTS) }
    }

    private fun restoreDriver(user: UserAdmin) {
        db.collection("drivers").document(user.id).update("status", "active")
            .addOnSuccessListener { loadArchive(ArchiveTab.DRIVERS) }
    }

    private fun restoreBus(bus: BusAdmin) {
        db.collection("buses").document(bus.id).update("status", "Active")
            .addOnSuccessListener { loadArchive(ArchiveTab.BUS) }
    }

    enum class ArchiveTab { PARENTS, DRIVERS, BUS }
}
