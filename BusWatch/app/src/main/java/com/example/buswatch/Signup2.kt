package com.example.buswatch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
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
import com.yalantis.ucrop.UCrop
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.Serializable

class Signup2 : AppCompatActivity() {

    private var avatarUri: Uri? = null
    private var selectedSuffix: String? = null
    private var selectedGrade: String? = null

    private lateinit var etChildFirstName: EditText
    private lateinit var etChildLastName: EditText
    private lateinit var etChildMiddleName: EditText
    private lateinit var tvSelectedSuffix: TextView
    private lateinit var etChildAge: EditText
    private lateinit var etChildClass: EditText
    private lateinit var tvSelectedGrade: TextView
    private lateinit var etChildSchool: EditText
    private lateinit var ivAvatar: ImageView
    private lateinit var tvChildNumber: TextView
    private lateinit var etPickupAddress: EditText
    private lateinit var mapView: MapView
    private lateinit var btnMyLocation: ImageButton
    
    private lateinit var tvFirstNameWarning: TextView
    private lateinit var tvLastNameWarning: TextView
    private lateinit var tvMiddleNameWarning: TextView
    private lateinit var tvAgeWarning: TextView

    private var childrenList = ArrayList<HashMap<String, Any?>>()
    private var currentChildIndex = 0

    private var lastSourceUriAvatar: Uri? = null
    private var currentMarker: Marker? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            lastSourceUriAvatar = it
            startCrop(it) 
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
        
        // Initialize osmdroid configuration
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
        etChildClass = findViewById(R.id.etChildClass)
        etChildSchool = findViewById(R.id.etSignup2School)
        ivAvatar = findViewById(R.id.imageView39)
        tvChildNumber = findViewById(R.id.tvChildNumber)
        etPickupAddress = findViewById(R.id.etSignup2PickupAddress)
        mapView = findViewById(R.id.mapSignup2)
        btnMyLocation = findViewById(R.id.btnSignup2MyLocation)

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

        // Setup real-time validation
        setupNameWatcher(etChildFirstName, tvFirstNameWarning)
        setupNameWatcher(etChildLastName, tvLastNameWarning)
        setupNameWatcher(etChildMiddleName, tvMiddleNameWarning)
        setupAgeWatcher()

        // Character limits
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

        // Restore state or initialize from intent
        if (savedInstanceState != null) {
            currentChildIndex = savedInstanceState.getInt("currentIndex", 0)
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            childrenList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("childrenList", ArrayList::class.java) as? ArrayList<HashMap<String, Any?>>
            } else {
                savedInstanceState.getSerializable("childrenList") as? ArrayList<HashMap<String, Any?>>
            } ?: ArrayList()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lastSourceUriAvatar = savedInstanceState.getParcelable("lastSourceUriAvatar", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                lastSourceUriAvatar = savedInstanceState.getParcelable("lastSourceUriAvatar")
            }
        } else {
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val additionalFromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("additionalChildren", ArrayList::class.java) as? ArrayList<HashMap<String, Any?>>
            } else {
                intent.getSerializableExtra("additionalChildren") as? ArrayList<HashMap<String, Any?>>
            }
            
            if (additionalFromIntent != null || intent.hasExtra("childFirstName")) {
                val firstChild = hashMapOf<String, Any?>(
                    "firstName" to (intent.getStringExtra("childFirstName") ?: ""),
                    "lastName" to (intent.getStringExtra("childLastName") ?: ""),
                    "middleName" to (intent.getStringExtra("childMiddleName") ?: ""),
                    "suffix" to (intent.getStringExtra("childSuffix") ?: ""),
                    "age" to (intent.getStringExtra("childAge") ?: ""),
                    "class" to (intent.getStringExtra("childClass") ?: ""),
                    "grade" to (intent.getStringExtra("childGrade") ?: ""),
                    "school" to (intent.getStringExtra("childSchool") ?: ""),
                    "address" to (intent.getStringExtra("childAddress") ?: ""),
                    "avatarUrl" to intent.getStringExtra("childAvatarUrl"),
                    "latitude" to intent.getDoubleExtra("childLatitude", 0.0).takeIf { it != 0.0 },
                    "longitude" to intent.getDoubleExtra("childLongitude", 0.0).takeIf { it != 0.0 }
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
                    putExtra("childClass", primaryChild["class"] as String)
                    putExtra("childGrade", primaryChild["grade"] as String)
                    putExtra("childSchool", primaryChild["school"] as String)
                    putExtra("childAddress", primaryChild["address"] as String)
                    putExtra("childAvatarUrl", primaryChild["avatarUrl"] as String?)
                    putExtra("childLatitude", primaryChild["latitude"] as? Double ?: 0.0)
                    putExtra("childLongitude", primaryChild["longitude"] as? Double ?: 0.0)

                    if (childrenList.size > 1) {
                        val additionalChildren = ArrayList(childrenList.subList(1, childrenList.size))
                        putExtra("additionalChildren", additionalChildren as Serializable)
                    }
                }
                signup3Launcher.launch(nextIntent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
                }
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
                p?.let { updateMarker(it) }
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
            } ?: run {
                Toast.makeText(this, "Unable to get current location. Make sure GPS is on.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMarker(point: GeoPoint) {
        if (currentMarker == null) {
            currentMarker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Pickup Location"
            }
            mapView.overlays.add(currentMarker)
        }
        currentMarker?.position = point
        selectedLatitude = point.latitude
        selectedLongitude = point.longitude
        mapView.invalidate()
    }

    private fun startCrop(uri: Uri) {
        val destinationFileName = "avatar_crop_${System.currentTimeMillis()}.jpg"
        val uCrop = UCrop.of(uri, Uri.fromFile(File(cacheDir, destinationFileName)))
        
        val options = UCrop.Options().apply {
            setToolbarColor(ContextCompat.getColor(this@Signup2, android.R.color.white))
            setToolbarWidgetColor(Color.BLACK)
            setActiveControlsWidgetColor(ContextCompat.getColor(this@Signup2, CommonR.color.yellow_primary))
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
        }

        uCrop.withAspectRatio(1f, 1f)

        uCrop.withOptions(options)
        uCrop.start(this, UCrop.REQUEST_CROP)
    }

    private fun showPreviewDialog(uri: Uri) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_photo, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ivAvatarPreview = dialogView.findViewById<ImageView>(R.id.ivPreviewAvatar)
        val cvAvatar = dialogView.findViewById<View>(R.id.cvAvatarPreview)
        val btnSave = dialogView.findViewById<Button>(R.id.btnPreviewSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnPreviewCancel)
        val btnDelete = dialogView.findViewById<ImageButton>(R.id.btnDeletePhoto)

        cvAvatar.visibility = View.VISIBLE
        Glide.with(this).load(uri).circleCrop().into(ivAvatarPreview)
        cvAvatar.setOnClickListener {
            dialog.dismiss()
            lastSourceUriAvatar?.let { startCrop(it) }
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            avatarUri = null
            ivAvatar.setImageResource(CommonR.drawable.user)
            lastSourceUriAvatar = null
            Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            dialog.dismiss()
            avatarUri = uri
            Glide.with(this).load(avatarUri).circleCrop().into(ivAvatar)
        }

        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val resultUri = UCrop.getOutput(data)
            if (resultUri != null) {
                showPreviewDialog(resultUri)
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Toast.makeText(this, "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNameWatcher(editText: EditText, warningView: TextView) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString() ?: ""
                if (isInvalidName(input)) {
                    warningView.visibility = View.VISIBLE
                } else {
                    warningView.visibility = View.GONE
                }
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
        outState.putParcelable("lastSourceUriAvatar", lastSourceUriAvatar)
        selectedLatitude?.let { outState.putDouble("selectedLat", it) }
        selectedLongitude?.let { outState.putDouble("selectedLon", it) }
    }

    private fun validateFields(): Boolean {
        val fName = etChildFirstName.text.toString().trim()
        val lName = etChildLastName.text.toString().trim()
        val ageStr = etChildAge.text.toString().trim()
        val age = ageStr.toIntOrNull() ?: 0
        val className = etChildClass.text.toString().trim()
        val grade = selectedGrade ?: ""
        val school = etChildSchool.text.toString().trim()
        val address = etPickupAddress.text.toString().trim()

        if (fName.isEmpty() || lName.isEmpty() || ageStr.isEmpty() || className.isEmpty() || grade.isEmpty() || school.isEmpty() || address.isEmpty() || avatarUri == null) {
            Toast.makeText(this, "Please fill in all required fields marked with *", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedLatitude == null || selectedLongitude == null) {
            Toast.makeText(this, "Please select the pickup location on the map", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (isInvalidName(fName) || isInvalidName(lName) || isInvalidName(etChildMiddleName.text.toString())) {
            Toast.makeText(this, "Please correct the errors in the name fields", Toast.LENGTH_SHORT).show()
            return false
        }

        if (age >= 21) {
            Toast.makeText(this, "Registration restricted for ages 21 and above.", Toast.LENGTH_SHORT).show()
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
            "class" to etChildClass.text.toString().trim(),
            "grade" to (selectedGrade ?: ""),
            "school" to etChildSchool.text.toString().trim(),
            "address" to etPickupAddress.text.toString().trim(),
            "avatarUrl" to avatarUri?.toString(),
            "latitude" to selectedLatitude,
            "longitude" to selectedLongitude
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
            etChildClass.setText(child["class"] as? String ?: "")
            
            selectedGrade = child["grade"] as? String ?: ""
            if (!selectedGrade.isNullOrEmpty()) {
                tvSelectedGrade.text = selectedGrade
                tvSelectedGrade.setTextColor(Color.BLACK)
            } else {
                tvSelectedGrade.text = getString(CommonR.string.select_grade)
                tvSelectedGrade.setTextColor(ContextCompat.getColor(this, CommonR.color.accessible_gray_text))
            }

            etChildSchool.setText(child["school"] as? String ?: "")
            etPickupAddress.setText(child["address"] as? String ?: "")
            
            avatarUri = (child["avatarUrl"] as? String)?.toUri()
            if (avatarUri != null) {
                Glide.with(this).load(avatarUri).circleCrop().into(ivAvatar)
            } else {
                ivAvatar.setImageResource(CommonR.drawable.user)
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
        etChildClass.text.clear()
        selectedGrade = ""
        tvSelectedGrade.text = getString(CommonR.string.select_grade)
        tvSelectedGrade.setTextColor(ContextCompat.getColor(this, CommonR.color.accessible_gray_text))
        etChildSchool.text.clear()
        etPickupAddress.text.clear()
        ivAvatar.setImageResource(CommonR.drawable.user)
        avatarUri = null
        removeMarker()
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
                putExtra("childClass", etChildClass.text.toString().trim())
                putExtra("childGrade", selectedGrade ?: "")
                putExtra("childSchool", etChildSchool.text.toString().trim())
                putExtra("childAddress", etPickupAddress.text.toString().trim())
                putExtra("childAvatarUrl", avatarUri?.toString())
                putExtra("childLatitude", selectedLatitude ?: 0.0)
                putExtra("childLongitude", selectedLongitude ?: 0.0)

                if (childrenList.size > 1) {
                    val additionalChildren = ArrayList(childrenList.subList(1, childrenList.size))
                    putExtra("additionalChildren", additionalChildren as Serializable)
                }
            }
            setResult(RESULT_OK, resultIntent)
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
            }
        }
    }
}
