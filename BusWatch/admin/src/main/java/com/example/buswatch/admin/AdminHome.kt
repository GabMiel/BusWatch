package com.example.buswatch.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class AdminHome : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    
    // Data storage
    private var activeUsers = mutableListOf<UserAdmin>()
    private var activeDrivers = mutableListOf<UserAdmin>()
    private var pendingUsers = mutableListOf<UserAdmin>()

    private var selectedImageUri: Uri? = null
    private var dialogImgAvatar: ImageView? = null

    private var selectedCountryCode = "+63"
    private var maxPhoneDigits = 10
    private var isPhoneFormatting = false
    private var selectedLanguage: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            dialogImgAvatar?.setImageURI(it)
        }
    }

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
            loadArchive()
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

    private fun loadDashboard() {
        loadLayout(R.layout.fragment_dashboard)
    }

    private fun loadUsers() {
        loadLayout(R.layout.fragment_users)
        
        findViewById<TextView>(R.id.tabDrivers)?.setOnClickListener {
            loadDrivers()
        }

        fetchParents()
    }

    private fun fetchParents() {
        db.collection("parents")
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { documents ->
                activeUsers.clear()
                for (document in documents) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    activeUsers.add(UserAdmin(document.id, "$firstName $lastName", "Parent", status = "approved"))
                }
                setupUserList()
            }
    }

    private fun loadDrivers() {
        loadLayout(R.layout.fragment_driver)
        
        findViewById<TextView>(R.id.tabParents)?.setOnClickListener {
            loadUsers()
        }

        findViewById<TextView>(R.id.btnAddNewDriver)?.setOnClickListener {
            showAddDriverDialog()
        }
        
        fetchDrivers()
    }
    
    private fun fetchDrivers() {
        db.collection("drivers")
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { documents ->
                activeDrivers.clear()
                for (document in documents) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    activeDrivers.add(UserAdmin(document.id, "$firstName $lastName", "Driver", status = "active"))
                }
                setupDriverList()
            }
    }

    private fun setupUserList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerUsers) ?: return
        val adapter = UserAdapter(activeUsers, 
            onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
            onArchiveClick = { user, _ -> 
                archiveUser(user)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun archiveUser(user: UserAdmin) {
        val collection = if (user.role == "Parent") "parents" else "drivers"
        db.collection(collection).document(user.id)
            .update("status", "archived")
            .addOnSuccessListener {
                if (user.role == "Parent") loadUsers() else loadDrivers()
            }
    }

    private fun setupDriverList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerUsers) ?: return
        val adapter = UserAdapter(activeDrivers, 
            onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
            onArchiveClick = { user, _ -> 
                archiveUser(user)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadApprovals(showMapRequests: Boolean = false) {
        loadLayout(R.layout.fragment_approvals)
        
        val tabReg = findViewById<TextView>(R.id.tabRegistration)
        val tabMap = findViewById<TextView>(R.id.tabMapLocations)

        tabReg?.setOnClickListener { loadApprovals(false) }
        tabMap?.setOnClickListener { loadApprovals(true) }

        if (showMapRequests) {
            tabReg?.setTextColor(ContextCompat.getColor(this, CommonR.color.gray_text))
            tabReg?.setBackgroundResource(CommonR.drawable.bg_tab_inactive)
            tabMap?.setTextColor(ContextCompat.getColor(this, CommonR.color.black))
            tabMap?.setBackgroundResource(CommonR.drawable.bg_tab_active)
            fetchMapLocationRequests()
        } else {
            tabReg?.setTextColor(ContextCompat.getColor(this, CommonR.color.black))
            tabReg?.setBackgroundResource(CommonR.drawable.bg_tab_active)
            tabMap?.setTextColor(ContextCompat.getColor(this, CommonR.color.gray_text))
            tabMap?.setBackgroundResource(CommonR.drawable.bg_tab_inactive)
            fetchPendingParentsForApprovals()
        }
    }

    private fun fetchPendingParentsForApprovals() {
        db.collection("parents")
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                pendingUsers.clear()
                for (document in documents) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    pendingUsers.add(UserAdmin(document.id, "$firstName $lastName", "Parent", status = "pending"))
                }
                setupApprovalsList()
            }
    }

    private fun fetchMapLocationRequests() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerApprovals) ?: return
        recyclerView.adapter = null 
        Toast.makeText(this, "Map location requests loading...", Toast.LENGTH_SHORT).show()
    }

    private fun setupApprovalsList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerApprovals) ?: return
        val adapter = PendingUserAdapter(pendingUsers,
            onAcceptClick = { user, pos -> approveUserFromApprovals(user, pos) },
            onRejectClick = { user, pos -> rejectUserFromApprovals(user, pos) },
            onViewClick = { user -> showUserDetailDialog(user, isPending = true) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun approveUserFromApprovals(user: UserAdmin, position: Int) {
        db.collection("parents").document(user.id)
            .update("status", "approved")
            .addOnSuccessListener {
                Toast.makeText(this, "${user.name} approved", Toast.LENGTH_SHORT).show()
                pendingUsers.removeAt(position)
                loadApprovals(false) 
            }
    }

    private fun rejectUserFromApprovals(user: UserAdmin, position: Int) {
        db.collection("parents").document(user.id)
            .update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(this, "${user.name} rejected", Toast.LENGTH_SHORT).show()
                pendingUsers.removeAt(position)
                loadApprovals(false)
            }
    }

    private fun loadArchive(showDrivers: Boolean = false) {
        if (showDrivers) {
            loadLayout(R.layout.fragment_driver_archive)
            fetchArchivedDrivers()
        } else {
            loadLayout(R.layout.fragment_archive)
            fetchArchivedParents()
        }
        
        findViewById<TextView>(R.id.tabArchivedParents)?.setOnClickListener {
            loadArchive(false)
        }

        findViewById<TextView>(R.id.tabArchivedDrivers)?.setOnClickListener {
            loadArchive(true)
        }
    }

    private fun fetchArchivedParents() {
        db.collection("parents")
            .whereIn("status", listOf("archived", "rejected"))
            .get()
            .addOnSuccessListener { documents ->
                val filteredList = mutableListOf<UserAdmin>()
                for (document in documents) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    filteredList.add(UserAdmin(document.id, "$firstName $lastName", "Parent", isArchived = true))
                }
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerArchived) ?: return@addOnSuccessListener
                val adapter = UserAdapter(filteredList,
                    onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
                    onArchiveClick = { user, _ ->
                        restoreUser(user)
                    }
                )
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = adapter
            }
    }
    
    private fun fetchArchivedDrivers() {
        db.collection("drivers")
            .whereEqualTo("status", "archived")
            .get()
            .addOnSuccessListener { documents ->
                val filteredList = mutableListOf<UserAdmin>()
                for (document in documents) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    filteredList.add(UserAdmin(document.id, "$firstName $lastName", "Driver", isArchived = true))
                }
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerArchived) ?: return@addOnSuccessListener
                val adapter = UserAdapter(filteredList,
                    onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
                    onArchiveClick = { user, _ ->
                        restoreDriver(user)
                    }
                )
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = adapter
            }
    }

    private fun restoreUser(user: UserAdmin) {
        db.collection("parents").document(user.id)
            .update("status", "approved")
            .addOnSuccessListener {
                loadArchive(false)
            }
    }
    
    private fun restoreDriver(user: UserAdmin) {
        db.collection("drivers").document(user.id)
            .update("status", "active")
            .addOnSuccessListener {
                loadArchive(true)
            }
    }

    private fun showUserDetailDialog(user: UserAdmin, isPending: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_parent, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.tvFirstName)?.text = user.name.split(" ").getOrNull(0) ?: user.name
        dialogView.findViewById<TextView>(R.id.tvLastName)?.text = user.name.split(" ").getOrNull(1) ?: ""

        dialogView.findViewById<ImageButton>(R.id.btnClose)?.setOnClickListener {
            dialog.dismiss()
        }

        if (isPending) {
            dialogView.findViewById<Button>(R.id.btnAccept)?.setOnClickListener {
                val pos = pendingUsers.indexOfFirst { it.id == user.id }
                if (pos != -1) approveUserFromApprovals(user, pos)
                dialog.dismiss()
            }
            dialogView.findViewById<Button>(R.id.btnReject)?.setOnClickListener {
                val pos = pendingUsers.indexOfFirst { it.id == user.id }
                if (pos != -1) rejectUserFromApprovals(user, pos)
                dialog.dismiss()
            }
        } else {
            dialogView.findViewById<LinearLayout>(R.id.layoutActionButtons)?.visibility = View.GONE
        }

        dialog.show()
    }

    private fun showAddDriverDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_driver, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        selectedImageUri = null
        dialogImgAvatar = dialogView.findViewById(R.id.imgDriverPhoto)
        val framePhoto = dialogView.findViewById<View>(R.id.frameDriverPhoto)
        framePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }

        val etFirstName = dialogView.findViewById<EditText>(R.id.etFirstName)
        val etMiddleName = dialogView.findViewById<EditText>(R.id.etMiddleName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etLastName)
        val tvSuffix = dialogView.findViewById<TextView>(R.id.tvSuffix)
        val btnSuffixDropdown = dialogView.findViewById<View>(R.id.btnSuffixDropdown)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val etContactNumber = dialogView.findViewById<EditText>(R.id.etContactNumber)
        val tvCountryCode = dialogView.findViewById<TextView>(R.id.tvCountryCode)
        val btnCountryCode = dialogView.findViewById<FrameLayout>(R.id.btnCountryCode)
        val etLicense = dialogView.findViewById<EditText>(R.id.etLicense)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etConfirmPassword)
        val tvLanguage = dialogView.findViewById<TextView>(R.id.tvLanguage)
        val btnLanguageDropdown = dialogView.findViewById<FrameLayout>(R.id.btnLanguageDropdown)
        val btnSaveDriver = dialogView.findViewById<TextView>(R.id.btnSaveDriver)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseAddDriver)

        val tvFirstNameWarn = dialogView.findViewById<TextView>(R.id.tvFirstNameWarning)
        val tvMiddleNameWarn = dialogView.findViewById<TextView>(R.id.tvMiddleNameWarning)
        val tvLastNameWarn = dialogView.findViewById<TextView>(R.id.tvLastNameWarning)
        val tvEmailWarn = dialogView.findViewById<TextView>(R.id.tvEmailWarning)
        val tvPhoneWarn = dialogView.findViewById<TextView>(R.id.tvPhoneWarning)
        val tvLicenseWarn = dialogView.findViewById<TextView>(R.id.tvLicenseWarning)
        val tvPasswordWarn = dialogView.findViewById<TextView>(R.id.tvPasswordWarning)
        val tvConfirmWarn = dialogView.findViewById<TextView>(R.id.tvConfirmPasswordWarning)

        val passwordRegex = Regex("^(?=.*[A-Z])(?=.*[0-9]).{8,}$")
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")

        // Reset variables
        selectedCountryCode = "+63"
        maxPhoneDigits = 10
        isPhoneFormatting = false
        selectedLanguage = null

        fun updatePhoneFilter() {
            val spaces = when (selectedCountryCode) {
                "+63", "+1", "+61", "+64", "+353" -> 2
                "+44", "+65" -> 1
                else -> 0
            }
            etContactNumber?.filters = arrayOf(InputFilter.LengthFilter(maxPhoneDigits + spaces))
        }

        fun applyPhoneFormatting(s: Editable) {
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
                val selection = etContactNumber?.selectionStart ?: 0
                val oldLength = s.length
                s.replace(0, s.length, formatted.toString())
                val newLength = formatted.length
                val newSelection = (selection + (newLength - oldLength)).coerceIn(0, newLength)
                etContactNumber?.setSelection(newSelection)
            }
        }

        fun validateFirstName(s: String) {
            if (s.isEmpty()) {
                tvFirstNameWarn?.visibility = View.VISIBLE
                tvFirstNameWarn?.setText(CommonR.string.first_name_required)
            } else if (isInvalidName(s)) {
                tvFirstNameWarn?.visibility = View.VISIBLE
                tvFirstNameWarn?.setText(CommonR.string.name_warning)
            } else {
                tvFirstNameWarn?.visibility = View.GONE
            }
        }

        fun validateMiddleName(s: String) {
            if (s.isNotEmpty() && isInvalidName(s)) {
                tvMiddleNameWarn?.visibility = View.VISIBLE
            } else {
                tvMiddleNameWarn?.visibility = View.GONE
            }
        }

        fun validateLastName(s: String) {
            if (s.isEmpty()) {
                tvLastNameWarn?.visibility = View.VISIBLE
                tvLastNameWarn?.setText(CommonR.string.last_name_required)
            } else if (isInvalidName(s)) {
                tvLastNameWarn?.visibility = View.VISIBLE
                tvLastNameWarn?.setText(CommonR.string.name_warning)
            } else {
                tvLastNameWarn?.visibility = View.GONE
            }
        }

        fun validateEmail(s: String) {
            if (s.isEmpty()) {
                tvEmailWarn?.visibility = View.VISIBLE
                tvEmailWarn?.setText(CommonR.string.email_required)
            } else if (!emailRegex.matches(s)) {
                tvEmailWarn?.visibility = View.VISIBLE
                tvEmailWarn?.setText(CommonR.string.invalid_email_format)
            } else {
                tvEmailWarn?.visibility = View.GONE
            }
        }

        fun validatePhone(s: String) {
            val digits = s.replace(" ", "")
            if (digits.isEmpty()) {
                tvPhoneWarn?.visibility = View.VISIBLE
                tvPhoneWarn?.setText(CommonR.string.phone_required)
            } else if (digits.length != maxPhoneDigits) {
                tvPhoneWarn?.visibility = View.VISIBLE
                tvPhoneWarn?.text = "Please enter a valid $maxPhoneDigits-digit phone number"
            } else {
                tvPhoneWarn?.visibility = View.GONE
            }
        }

        fun validateLicense(s: String) {
            if (s.isEmpty()) {
                tvLicenseWarn?.visibility = View.VISIBLE
            } else {
                tvLicenseWarn?.visibility = View.GONE
            }
        }

        fun validatePassword(s: String) {
            if (s.isEmpty()) {
                tvPasswordWarn?.visibility = View.VISIBLE
                tvPasswordWarn?.setText(CommonR.string.password_required)
            } else if (!passwordRegex.matches(s)) {
                tvPasswordWarn?.visibility = View.VISIBLE
                tvPasswordWarn?.setText(CommonR.string.password_validation_hint)
            } else {
                tvPasswordWarn?.visibility = View.GONE
            }
        }

        fun validateConfirmPassword(s: String) {
            val pass = etPassword?.text.toString()
            if (s != pass) {
                tvConfirmWarn?.visibility = View.VISIBLE
            } else {
                tvConfirmWarn?.visibility = View.GONE
            }
        }

        etFirstName?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validateFirstName(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        etMiddleName?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validateMiddleName(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        etLastName?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validateLastName(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        etEmail?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validateEmail(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        updatePhoneFilter()
        etContactNumber?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validatePhone(s.toString()) }
            override fun afterTextChanged(s: Editable?) {
                if (isPhoneFormatting || s == null) return
                isPhoneFormatting = true
                applyPhoneFormatting(s)
                isPhoneFormatting = false
            }
        })

        btnCountryCode?.setOnClickListener {
            val countries = arrayOf("Philippines (+63)", "USA/Canada (+1)", "UK (+44)", "Australia (+61)", "New Zealand (+64)", "Singapore (+65)", "Ireland (+353)")
            val codesOnly = arrayOf("+63", "+1", "+44", "+61", "+64", "+65", "+353")
            val lengths = arrayOf(10, 10, 10, 9, 10, 8, 9)
            
            AlertDialog.Builder(this)
                .setTitle("Select Country Code")
                .setItems(countries) { _, which ->
                    selectedCountryCode = codesOnly[which]
                    maxPhoneDigits = lengths[which]
                    tvCountryCode?.text = selectedCountryCode
                    etContactNumber?.text?.clear()
                    updatePhoneFilter()
                }
                .show()
        }

        etLicense?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validateLicense(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        etPassword?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePassword(s.toString())
                validateConfirmPassword(etConfirmPassword?.text.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etConfirmPassword?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validateConfirmPassword(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnLanguageDropdown?.setOnClickListener {
            val languages = arrayOf("English", "Filipino")
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, languages) {
                override fun isEnabled(position: Int) = position == 0
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val tv = view.findViewById<TextView>(android.R.id.text1)
                    tv.setTextColor(if (position == 1) android.graphics.Color.LTGRAY else android.graphics.Color.BLACK)
                    return view
                }
            }

            AlertDialog.Builder(this).setTitle(getString(CommonR.string.select_language)).setAdapter(adapter) { _, which ->
                if (which == 0) {
                    selectedLanguage = languages[which]
                    tvLanguage?.text = selectedLanguage
                    tvLanguage?.setTextColor(android.graphics.Color.BLACK)
                }
            }.show()
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        
        btnSuffixDropdown?.setOnClickListener {
            val suffixes = resources.getStringArray(CommonR.array.suffixes_array)
            AlertDialog.Builder(this)
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, which ->
                    tvSuffix?.text = suffixes[which]
                }
                .show()
        }
        
        btnSaveDriver?.setOnClickListener {
            val first = etFirstName?.text.toString().trim()
            val middle = etMiddleName?.text.toString().trim()
            val last = etLastName?.text.toString().trim()
            val suffix = if (tvSuffix?.text == "Suffix" || tvSuffix?.text == "None") "" else tvSuffix?.text.toString()
            val email = etEmail?.text.toString().trim()
            val phoneRaw = etContactNumber?.text.toString().replace(" ", "")
            val license = etLicense?.text.toString().trim()
            val pass = etPassword?.text.toString()
            val confirmPass = etConfirmPassword?.text.toString()

            validateFirstName(first)
            validateMiddleName(middle)
            validateLastName(last)
            validateEmail(email)
            validatePhone(etContactNumber?.text.toString())
            validateLicense(license)
            validatePassword(pass)
            validateConfirmPassword(confirmPass)

            if (tvFirstNameWarn?.visibility == View.VISIBLE || tvMiddleNameWarn?.visibility == View.VISIBLE ||
                tvLastNameWarn?.visibility == View.VISIBLE || tvEmailWarn?.visibility == View.VISIBLE ||
                tvPhoneWarn?.visibility == View.VISIBLE || tvLicenseWarn?.visibility == View.VISIBLE ||
                tvPasswordWarn?.visibility == View.VISIBLE || tvConfirmWarn?.visibility == View.VISIBLE) {
                Toast.makeText(this, getString(CommonR.string.please_fix_errors), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (first.isEmpty() || last.isEmpty() || email.isEmpty() || phoneRaw.isEmpty() || license.isEmpty() || pass.isEmpty() || selectedLanguage == null) {
                Toast.makeText(this, getString(CommonR.string.fill_all_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSaveDriver.isEnabled = false
            Toast.makeText(this, "Creating driver account...", Toast.LENGTH_SHORT).show()
            
            val fullPhone = "$selectedCountryCode $phoneRaw"

            // 1. Create User in Firebase Auth
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: ""
                    
                    if (selectedImageUri != null) {
                        uploadDriverPhoto(uid, first, last, middle, suffix, email, fullPhone, license, dialog, btnSaveDriver)
                    } else {
                        saveDriverToFirestore(uid, first, last, middle, suffix, email, fullPhone, license, null, dialog, btnSaveDriver)
                    }
                }
                .addOnFailureListener { e ->
                    btnSaveDriver.isEnabled = true
                    Toast.makeText(this, "Error creating account: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun uploadDriverPhoto(uid: String, first: String, last: String, middle: String, suffix: String, email: String, phone: String, license: String, dialog: AlertDialog, btnSave: View) {
        val ref = storage.reference.child("drivers/$uid/avatar.jpg")
        ref.putFile(selectedImageUri!!)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }
            .addOnSuccessListener { url ->
                saveDriverToFirestore(uid, first, last, middle, suffix, email, phone, license, url.toString(), dialog, btnSave)
            }
            .addOnFailureListener { e ->
                btnSave.isEnabled = true
                Toast.makeText(this, "Photo upload failed: ${e.message}. Saving without photo.", Toast.LENGTH_SHORT).show()
                saveDriverToFirestore(uid, first, last, middle, suffix, email, phone, license, null, dialog, btnSave)
            }
    }

    private fun saveDriverToFirestore(uid: String, first: String, last: String, middle: String, suffix: String, email: String, phone: String, license: String, avatarUrl: String?, dialog: AlertDialog, btnSave: View) {
        val driverData = hashMapOf(
            "uid" to uid,
            "firstName" to first,
            "middleName" to middle,
            "lastName" to last,
            "suffix" to suffix,
            "email" to email,
            "phone" to phone,
            "licenseNumber" to license,
            "driverAvatar" to (avatarUrl ?: ""),
            "preferredLanguage" to (selectedLanguage ?: "English"),
            "role" to "driver",
            "status" to "active"
        )
        
        db.collection("drivers").document(uid).set(driverData)
            .addOnSuccessListener {
                Toast.makeText(this, getString(CommonR.string.driver_added_success), Toast.LENGTH_SHORT).show()
                fetchDrivers()
                dialog.dismiss()
            }
            .addOnFailureListener { e ->
                btnSave.isEnabled = true
                Toast.makeText(this, "Error saving driver: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun isInvalidName(name: String): Boolean {
        if (name.isEmpty()) return false
        val regex = Regex("^[a-zA-Z\\s.-]*$")
        return !regex.matches(name)
    }

    private fun loadLayout(layoutResId: Int) {
        val container = findViewById<android.widget.FrameLayout>(R.id.fragmentContainer)
        container.removeAllViews()
        layoutInflater.inflate(layoutResId, container, true)
    }
}
