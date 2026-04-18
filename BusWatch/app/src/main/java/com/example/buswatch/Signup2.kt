package com.example.buswatch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.Serializable
import java.util.Locale

class Signup2 : AppCompatActivity() {

    private var avatarUri: Uri? = null
    private var selectedSuffix: String? = null
    private var selectedGrade: String? = null
    private var selectedBloodType: String? = "Select blood type"

    private lateinit var etChildFirstName: EditText
    private lateinit var etChildLastName: EditText
    private lateinit var etChildMiddleName: EditText
    private lateinit var tvSelectedSuffix: TextView
    private lateinit var etChildAge: EditText
    private lateinit var etChildSection: EditText
    private lateinit var tvSelectedGrade: TextView
    private lateinit var etChildSchool: EditText
    private lateinit var ivAvatar: ImageView
    private lateinit var tvChildNumber: TextView
    private lateinit var etHomeAddress: EditText
    private lateinit var mapView: MapView
    private lateinit var btnMyLocation: ImageButton
    
    private lateinit var tvFirstNameWarning: TextView
    private lateinit var tvLastNameWarning: TextView
    private lateinit var tvMiddleNameWarning: TextView
    private lateinit var tvAgeWarning: TextView

    private lateinit var tvSelectedBloodType: TextView
    private lateinit var etChildAllergies: EditText
    private lateinit var etChildMedications: EditText
    private lateinit var etChildConditions: EditText

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var childrenList = ArrayList<HashMap<String, Any?>>()
    private var currentChildIndex = 0

    private var currentMarker: Marker? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            avatarUri = it
            Glide.with(this).load(it).circleCrop().into(ivAvatar)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val signup3Launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.extras?.let { intent.putExtras(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        
        setContentView(R.layout.signup2)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        etChildFirstName = findViewById(R.id.etChildFirstName)
        etChildLastName = findViewById(R.id.etChildLastName)
        etChildMiddleName = findViewById(R.id.etChildMiddleName)
        
        tvFirstNameWarning = findViewById(R.id.tvChildFirstNameWarning)
        tvLastNameWarning = findViewById(R.id.tvChildLastNameWarning)
        tvMiddleNameWarning = findViewById(R.id.tvChildMiddleNameWarning)
        tvAgeWarning = findViewById(R.id.tvChildAgeWarning)
        
        val suffixSelector = findViewById<FrameLayout>(R.id.btnSignup2Suffix)
        tvSelectedSuffix = findViewById(R.id.tvSignup2SelectedSuffix)
        
        val gradeSelector = findViewById<FrameLayout>(R.id.btnSignup2Grade)
        tvSelectedGrade = findViewById(R.id.tvSignup2SelectedGrade)
        
        etChildAge = findViewById(R.id.etChildAge)
        etChildSection = findViewById(R.id.etChildSection)
        etChildSchool = findViewById(R.id.etSignup2School)
        ivAvatar = findViewById(R.id.imageView39)
        tvChildNumber = findViewById(R.id.tvChildNumber)
        etHomeAddress = findViewById(R.id.etSignup2HomeAddress)
        mapView = findViewById(R.id.mapSignup2)
        btnMyLocation = findViewById(R.id.btnSignup2MyLocation)

        val bloodTypeSelector = findViewById<FrameLayout>(R.id.btnSignup2BloodType)
        tvSelectedBloodType = findViewById(R.id.tvSignup2SelectedBloodType)
        etChildAllergies = findViewById(R.id.etChildAllergies)
        etChildMedications = findViewById(R.id.etChildMedications)
        etChildConditions = findViewById(R.id.etChildConditions)

        val btnAddPhoto = findViewById<Button>(R.id.btnSignup2AddPhoto)
        val backButton = findViewById<Button>(R.id.btnSignup2Back)
        val nextButton = findViewById<Button>(R.id.btnSignup2Next)
        val btnAddChild = findViewById<Button>(R.id.btnSignup2AddChild)

        setupMapView()

        btnMyLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        setupNameWatcher(etChildFirstName, tvFirstNameWarning)
        setupNameWatcher(etChildLastName, tvLastNameWarning)
        setupNameWatcher(etChildMiddleName, tvMiddleNameWarning)
        setupAgeWatcher()

        etChildFirstName.filters = arrayOf(InputFilter.LengthFilter(50))
        etChildLastName.filters = arrayOf(InputFilter.LengthFilter(50))
        etChildMiddleName.filters = arrayOf(InputFilter.LengthFilter(20))

        suffixSelector.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(this)
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, which ->
                    selectedSuffix = if (which == 0) "" else suffixes[which]
                    tvSelectedSuffix.text = if (which == 0) getString(CommonR.string.suffix) else suffixes[which]
                    tvSelectedSuffix.setTextColor(if (which == 0) ContextCompat.getColor(this, CommonR.color.accessible_gray_text) else Color.BLACK)
                }
                .show()
        }

        gradeSelector.setOnClickListener {
            val grades = arrayOf("Nursery", "Kinder", "Prep", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6")
            AlertDialog.Builder(this)
                .setTitle("Select Grade")
                .setItems(grades) { _, which ->
                    selectedGrade = grades[which]
                    tvSelectedGrade.text = selectedGrade
                    tvSelectedGrade.setTextColor(Color.BLACK)
                }
                .show()
        }

        bloodTypeSelector.setOnClickListener {
            val bloodTypes = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown")
            AlertDialog.Builder(this)
                .setTitle("Select Blood Type")
                .setItems(bloodTypes) { _, which ->
                    selectedBloodType = bloodTypes[which]
                    tvSelectedBloodType.text = selectedBloodType
                    tvSelectedBloodType.setTextColor(Color.BLACK)
                }
                .show()
        }

        if (savedInstanceState != null) {
            currentChildIndex = savedInstanceState.getInt("currentIndex", 0)
            @Suppress("UNCHECKED_CAST")
            childrenList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("childrenList", ArrayList::class.java) as? ArrayList<HashMap<String, Any?>>
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getSerializable("childrenList") as? ArrayList<HashMap<String, Any?>>
            } ?: ArrayList()
        } else {
            @Suppress("UNCHECKED_CAST")
            val additionalFromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("additionalChildren", ArrayList::class.java) as? ArrayList<HashMap<String, Any?>>
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("additionalChildren") as? ArrayList<HashMap<String, Any?>>
            }
            
            if (additionalFromIntent != null || intent.hasExtra("childFirstName")) {
                val avatarFromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("childAvatarUri", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("childAvatarUri")
                }

                val firstChild = hashMapOf<String, Any?>(
                    "firstName" to (intent.getStringExtra("childFirstName") ?: ""),
                    "lastName" to (intent.getStringExtra("childLastName") ?: ""),
                    "middleName" to (intent.getStringExtra("childMiddleName") ?: ""),
                    "suffix" to (intent.getStringExtra("childSuffix") ?: ""),
                    "age" to (intent.getStringExtra("childAge") ?: ""),
                    "section" to (intent.getStringExtra("childSection") ?: ""),
                    "grade" to (intent.getStringExtra("childGrade") ?: ""),
                    "school" to (intent.getStringExtra("childSchool") ?: ""),
                    "address" to (intent.getStringExtra("childAddress") ?: ""),
                    "childAvatarUri" to (avatarFromIntent ?: (intent.getStringExtra("childAvatarUrl"))?.toUri())?.toString(),
                    "latitude" to intent.getDoubleExtra("childLatitude", 0.0).takeIf { it != 0.0 },
                    "longitude" to intent.getDoubleExtra("childLongitude", 0.0).takeIf { it != 0.0 },
                    "bloodType" to (intent.getStringExtra("childBloodType") ?: ""),
                    "allergies" to (intent.getStringExtra("childAllergies") ?: ""),
                    "medications" to (intent.getStringExtra("childMedications") ?: ""),
                    "conditions" to (intent.getStringExtra("childConditions") ?: "")
                )
                childrenList.add(firstChild)
                additionalFromIntent?.let { childrenList.addAll(it) }
            }
        }

        loadCurrentChildData()

        btnAddPhoto.setOnClickListener { pickAvatarLauncher.launch("image/*") }

        backButton.setOnClickListener { goBack() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })

        btnAddChild.setOnClickListener {
            if (validateFields()) {
                saveCurrentChildToList()
                currentChildIndex++
                clearFields()
                updateChildHeader()
                Toast.makeText(this, getString(CommonR.string.child_number_saved, currentChildIndex, currentChildIndex + 1), Toast.LENGTH_SHORT).show()
            }
        }

        nextButton.setOnClickListener {
            if (validateFields()) {
                saveCurrentChildToList()
                
                val nextIntent = Intent(this, Signup3::class.java).apply {
                    putExtras(this@Signup2.intent)
                    val primaryChild = childrenList[0]
                    putExtra("childFirstName", primaryChild["firstName"] as String)
                    putExtra("childLastName", primaryChild["lastName"] as String)
                    putExtra("childMiddleName", primaryChild["middleName"] as String)
                    putExtra("childSuffix", primaryChild["suffix"] as String)
                    putExtra("childAge", primaryChild["age"] as String)
                    putExtra("childSection", primaryChild["section"] as String)
                    putExtra("childGrade", primaryChild["grade"] as String)
                    putExtra("childSchool", primaryChild["school"] as String)
                    putExtra("childAddress", primaryChild["address"] as String)
                    
                    val uriString = primaryChild["childAvatarUri"] as? String
                    putExtra("childAvatarUri", uriString?.toUri())
                    
                    putExtra("childLatitude", primaryChild["latitude"] as? Double ?: 0.0)
                    putExtra("childLongitude", primaryChild["longitude"] as? Double ?: 0.0)
                    
                    putExtra("childBloodType", primaryChild["bloodType"] as? String ?: "")
                    putExtra("childAllergies", primaryChild["allergies"] as? String ?: "")
                    putExtra("childMedications", primaryChild["medications"] as? String ?: "")
                    putExtra("childConditions", primaryChild["conditions"] as? String ?: "")

                    if (childrenList.size > 1) {
                        val additionalChildren = ArrayList(childrenList.subList(1, childrenList.size))
                        putExtra("additionalChildren", additionalChildren as Serializable)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                signup3Launcher.launch(nextIntent)
            }
        }
    }

    private fun setupMapView() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        val mapController = mapView.controller
        mapController.setZoom(17.0)
        
        val startPoint = GeoPoint(14.7566, 121.0450)
        mapController.setCenter(startPoint)

        val receive = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { 
                    updateMarker(it)
                    getAddressFromLocation(it.latitude, it.longitude)
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        mapView.overlays.add(MapEventsOverlay(receive))
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val userPoint = GeoPoint(it.latitude, it.longitude)
                mapView.controller.animateTo(userPoint)
                updateMarker(userPoint)
                getAddressFromLocation(it.latitude, it.longitude)
            } ?: run {
                Toast.makeText(this, "Unable to get current location. Make sure GPS is on.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0].getAddressLine(0)
                        runOnUiThread {
                            etHomeAddress.setText(address)
                            etHomeAddress.setSelection(etHomeAddress.text.length)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0].getAddressLine(0)
                    etHomeAddress.setText(address)
                    etHomeAddress.setSelection(etHomeAddress.text.length)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMarker(point: GeoPoint) {
        if (currentMarker == null) {
            currentMarker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Home Location"
            }
            mapView.overlays.add(currentMarker)
        }
        currentMarker?.position = point
        selectedLatitude = point.latitude
        selectedLongitude = point.longitude
        mapView.invalidate()
    }

    private fun setupNameWatcher(editText: EditText, warningView: TextView) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString() ?: ""
                warningView.visibility = if (isInvalidName(input)) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupAgeWatcher() {
        etChildAge.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val ageStr = s?.toString() ?: ""
                if (ageStr.isNotEmpty()) {
                    val age = ageStr.toIntOrNull() ?: 0
                    when {
                        age < 3 -> {
                            tvAgeWarning.text = getString(CommonR.string.age_warning_young)
                            tvAgeWarning.visibility = View.VISIBLE
                            tvAgeWarning.setTextColor(ContextCompat.getColor(this@Signup2, CommonR.color.warning_brown))
                        }
                        age in 16..20 -> {
                            tvAgeWarning.text = getString(CommonR.string.age_warning_old)
                            tvAgeWarning.visibility = View.VISIBLE
                            tvAgeWarning.setTextColor(ContextCompat.getColor(this@Signup2, CommonR.color.warning_brown))
                        }
                        age >= 21 -> {
                            tvAgeWarning.text = getString(CommonR.string.age_warning_restricted)
                            tvAgeWarning.visibility = View.VISIBLE
                            tvAgeWarning.setTextColor(ContextCompat.getColor(this@Signup2, CommonR.color.accessible_error_red))
                        }
                        else -> {
                            tvAgeWarning.visibility = View.GONE
                        }
                    }
                } else {
                    tvAgeWarning.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun isInvalidName(name: String): Boolean {
        if (name.isEmpty()) return false
        val regex = Regex("^[a-zA-Z\\s.-]*$")
        return !regex.matches(name)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentIndex", currentChildIndex)
        outState.putSerializable("childrenList", childrenList)
    }

    private fun validateFields(): Boolean {
        val fName = etChildFirstName.text.toString().trim()
        val lName = etChildLastName.text.toString().trim()
        val ageStr = etChildAge.text.toString().trim()
        val sectionName = etChildSection.text.toString().trim()
        val grade = selectedGrade ?: ""
        val school = etChildSchool.text.toString().trim()
        val address = etHomeAddress.text.toString().trim()

        if (fName.isEmpty() || lName.isEmpty() || ageStr.isEmpty() || sectionName.isEmpty() || grade.isEmpty() || school.isEmpty() || address.isEmpty() || avatarUri == null) {
            Toast.makeText(this, "Please fill in all required fields marked with *", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedLatitude == null || selectedLongitude == null) {
            Toast.makeText(this, "Please select the home location on the map", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (isInvalidName(fName) || isInvalidName(lName) || isInvalidName(etChildMiddleName.text.toString())) {
            Toast.makeText(this, "Please correct the errors in the name fields", Toast.LENGTH_SHORT).show()
            return false
        }

        val age = ageStr.toIntOrNull() ?: 0
        if (age >= 26) {
            Toast.makeText(this, "Registration restricted for ages 26 and above.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveCurrentChildToList() {
        val childData = hashMapOf<String, Any?>(
            "firstName" to etChildFirstName.text.toString().trim(),
            "lastName" to etChildLastName.text.toString().trim(),
            "middleName" to etChildMiddleName.text.toString().trim(),
            "suffix" to (selectedSuffix ?: ""),
            "age" to etChildAge.text.toString().trim(),
            "section" to etChildSection.text.toString().trim(),
            "grade" to (selectedGrade ?: ""),
            "school" to etChildSchool.text.toString().trim(),
            "address" to etHomeAddress.text.toString().trim(),
            "childAvatarUri" to avatarUri?.toString(),
            "latitude" to selectedLatitude,
            "longitude" to selectedLongitude,
            "bloodType" to if (selectedBloodType == "Select blood type") "" else selectedBloodType,
            "allergies" to etChildAllergies.text.toString().trim(),
            "medications" to etChildMedications.text.toString().trim(),
            "conditions" to etChildConditions.text.toString().trim()
        )
        
        if (currentChildIndex < childrenList.size) {
            childrenList[currentChildIndex] = childData
        } else {
            childrenList.add(childData)
        }
    }

    private fun loadCurrentChildData() {
        if (currentChildIndex < childrenList.size) {
            val child = childrenList[currentChildIndex]
            etChildFirstName.setText(child["firstName"] as? String ?: "")
            etChildLastName.setText(child["lastName"] as? String ?: "")
            etChildMiddleName.setText(child["middleName"] as? String ?: "")
            
            selectedSuffix = child["suffix"] as? String ?: ""
            if (!selectedSuffix.isNullOrEmpty()) {
                tvSelectedSuffix.text = selectedSuffix
                tvSelectedSuffix.setTextColor(Color.BLACK)
            } else {
                tvSelectedSuffix.text = getString(CommonR.string.suffix)
                tvSelectedSuffix.setTextColor(ContextCompat.getColor(this, CommonR.color.accessible_gray_text))
            }

            etChildAge.setText(child["age"] as? String ?: "")
            etChildSection.setText(child["section"] as? String ?: "")
            
            selectedGrade = child["grade"] as? String ?: ""
            if (!selectedGrade.isNullOrEmpty()) {
                tvSelectedGrade.text = selectedGrade
                tvSelectedGrade.setTextColor(Color.BLACK)
            } else {
                tvSelectedGrade.text = getString(CommonR.string.select_grade)
                tvSelectedGrade.setTextColor(ContextCompat.getColor(this, CommonR.color.accessible_gray_text))
            }

            etChildSchool.setText(child["school"] as? String ?: "")
            etHomeAddress.setText(child["address"] as? String ?: "")
            
            val uriString = child["childAvatarUri"] as? String
            avatarUri = uriString?.toUri()
            if (avatarUri != null) {
                Glide.with(this).load(avatarUri).circleCrop().into(ivAvatar)
            } else {
                ivAvatar.setImageResource(CommonR.drawable.child)
            }

            selectedLatitude = child["latitude"] as? Double
            selectedLongitude = child["longitude"] as? Double
            if (selectedLatitude != null && selectedLongitude != null) {
                val point = GeoPoint(selectedLatitude!!, selectedLongitude!!)
                updateMarker(point)
                mapView.controller.setCenter(point)
            } else {
                removeMarker()
            }

            selectedBloodType = child["bloodType"] as? String ?: "Select blood type"
            if (selectedBloodType != "Select blood type" && !selectedBloodType.isNullOrEmpty()) {
                tvSelectedBloodType.text = selectedBloodType
                tvSelectedBloodType.setTextColor(Color.BLACK)
            } else {
                tvSelectedBloodType.text = getString(CommonR.string.select_blood_type)
                tvSelectedBloodType.setTextColor(ContextCompat.getColor(this, CommonR.color.accessible_gray_text))
            }
            etChildAllergies.setText(child["allergies"] as? String ?: "")
            etChildMedications.setText(child["medications"] as? String ?: "")
            etChildConditions.setText(child["conditions"] as? String ?: "")

        } else {
            clearFields()
        }
        updateChildHeader()
    }

    private fun removeMarker() {
        currentMarker?.let {
            mapView.overlays.remove(it)
            currentMarker = null
            mapView.invalidate()
        }
        selectedLatitude = null
        selectedLongitude = null
    }

    private fun clearFields() {
        etChildFirstName.text.clear()
        etChildLastName.text.clear()
        etChildMiddleName.text.clear()
        selectedSuffix = ""
        tvSelectedSuffix.text = getString(CommonR.string.suffix)
        tvSelectedSuffix.setTextColor(ContextCompat.getColor(this, CommonR.color.accessible_gray_text))
        etChildAge.text.clear()
        etChildSection.text.clear()
        selectedGrade = ""
        tvSelectedGrade.text = getString(CommonR.string.select_grade)
        tvSelectedGrade.setTextColor(ContextCompat.getColor(this, CommonR.color.accessible_gray_text))
        etChildSchool.text.clear()
        etHomeAddress.text.clear()
        ivAvatar.setImageResource(CommonR.drawable.child)
        avatarUri = null
        removeMarker()
        
        selectedBloodType = "Select blood type"
        tvSelectedBloodType.text = getString(CommonR.string.select_blood_type)
        tvSelectedBloodType.setTextColor(ContextCompat.getColor(this, CommonR.color.accessible_gray_text))
        etChildAllergies.text.clear()
        etChildMedications.text.clear()
        etChildConditions.text.clear()
    }

    private fun updateChildHeader() {
        tvChildNumber.text = getString(CommonR.string.child_n, currentChildIndex + 1)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    private fun goBack() {
        if (currentChildIndex > 0) {
            if (etChildFirstName.text.isNotEmpty()) {
                saveCurrentChildToList()
            }
            currentChildIndex--
            loadCurrentChildData()
        } else {
            val resultIntent = Intent().apply {
                putExtras(intent)
                putExtra("childFirstName", etChildFirstName.text.toString().trim())
                putExtra("childLastName", etChildLastName.text.toString().trim())
                putExtra("childMiddleName", etChildMiddleName.text.toString().trim())
                putExtra("childSuffix", selectedSuffix ?: "")
                putExtra("childAge", etChildAge.text.toString().trim())
                putExtra("childSection", etChildSection.text.toString().trim())
                putExtra("childGrade", selectedGrade ?: "")
                putExtra("childSchool", etChildSchool.text.toString().trim())
                putExtra("childAddress", etHomeAddress.text.toString().trim())
                
                putExtra("childAvatarUri", avatarUri)
                
                putExtra("childLatitude", selectedLatitude ?: 0.0)
                putExtra("childLongitude", selectedLongitude ?: 0.0)

                putExtra("childBloodType", if (selectedBloodType == "Select blood type") "" else selectedBloodType)
                putExtra("childAllergies", etChildAllergies.text.toString().trim())
                putExtra("childMedications", etChildMedications.text.toString().trim())
                putExtra("childConditions", etChildConditions.text.toString().trim())

                if (childrenList.size > 1) {
                    val additionalChildren = ArrayList(childrenList.subList(1, childrenList.size))
                    putExtra("additionalChildren", additionalChildren as Serializable)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}
