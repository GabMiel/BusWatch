package com.example.buswatch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale
import java.util.UUID

class StudentDetailsGeneralFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var studentId: String? = null
    private var parentId: String? = null
    private var childName: String? = null
    private var studentData: kotlin.collections.Map<String, Any>? = null
    private var studentListener: ListenerRegistration? = null

    private lateinit var imgStudent: ImageView
    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentId: TextView
    private lateinit var tvDob: TextView
    private lateinit var tvClass: TextView
    private lateinit var tvGrade: TextView
    private lateinit var tvSchool: TextView
    private lateinit var tvHomeAddress: TextView
    private lateinit var btnEditGeneral: ImageButton
    private lateinit var mapPreview: MapView

    private var selectedImageUri: Uri? = null
    private lateinit var imgEditAvatar: ImageView

    private var selectedGeoPoint: GeoPoint? = null

    companion object {
        fun newInstance(childName: String?): StudentDetailsGeneralFragment {
            val fragment = StudentDetailsGeneralFragment()
            val args = Bundle()
            args.putString("childName", childName)
            fragment.arguments = args
            return fragment
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childName = arguments?.getString("childName")
        studentId = arguments?.getString("studentId")
        parentId = arguments?.getString("parentId") ?: FirebaseAuth.getInstance().currentUser?.uid
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        context?.let { ctx ->
            Configuration.getInstance().load(ctx, 
                ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_student_details_general, container, false)
        
        imgStudent = view.findViewById(R.id.imgStudentAvatar)
        tvStudentName = view.findViewById(R.id.tvStudentName)
        tvStudentId = view.findViewById(R.id.tvStudentId)
        tvDob = view.findViewById(R.id.tvDob)
        tvClass = view.findViewById(R.id.tvClass)
        tvGrade = view.findViewById(R.id.tvGrade)
        tvSchool = view.findViewById(R.id.tvSchool)
        tvHomeAddress = view.findViewById(R.id.tvAddress)
        btnEditGeneral = view.findViewById(R.id.btnGeneralEdit)
        mapPreview = view.findViewById(R.id.mapPickupLocation)

        setupMapPreview()
        fetchStudentData()

        btnEditGeneral.setOnClickListener {
            showEditGeneralDialog()
        }

        return view
    }

    private fun setupMapPreview() {
        if (::mapPreview.isInitialized) {
            mapPreview.setMultiTouchControls(false)
            mapPreview.controller.setZoom(16.0)
        }
    }

    private fun fetchStudentData() {
        if (parentId == null) return
        studentListener?.remove()

        val parentDoc = db.collection("parents").document(parentId!!)

        if (studentId != null) {
            studentListener = parentDoc.collection("students").document(studentId!!)
                .addSnapshotListener { document, _ ->
                    if (!isAdded || document == null || !document.exists()) return@addSnapshotListener
                    studentData = document.data
                    studentData?.let { updateUI(it) }
                }
        } else if (childName != null) {
            studentListener = parentDoc.addSnapshotListener { document, _ ->
                if (!isAdded || document == null || !document.exists()) return@addSnapshotListener
                
                @Suppress("UNCHECKED_CAST")
                val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                
                var foundChild: kotlin.collections.Map<String, Any>? = null
                if (childMap != null) {
                    val firstName = childMap["firstName"] as? String ?: ""
                    val lastName = childMap["lastName"] as? String ?: ""
                    if ("$firstName $lastName".trim() == childName) foundChild = childMap
                }
                if (foundChild == null && childrenList != null) {
                    foundChild = childrenList.find { 
                        val f = it["firstName"] as? String ?: ""
                        val l = it["lastName"] as? String ?: ""
                        "$f $l".trim() == childName 
                    }
                }
                
                studentData = foundChild
                studentData?.let { updateUI(it) }
            }
        }
    }

    private fun updateUI(data: kotlin.collections.Map<String, Any>) {
        if (!isAdded || view == null) return
        val context = context ?: return
        
        val firstName = data["firstName"] as? String ?: ""
        val lastName = data["lastName"] as? String ?: ""
        tvStudentName.text = context.getString(CommonR.string.name_format, firstName, lastName)
        tvStudentId.text = data["studentId"] as? String ?: ""
        tvDob.text = data["dob"] as? String ?: ""
        tvClass.text = data["class"] as? String ?: ""
        tvGrade.text = data["grade"] as? String ?: ""
        tvSchool.text = data["school"] as? String ?: ""
        tvHomeAddress.text = data["homeAddress"] as? String ?: "No address set"

        val imageUrl = data["imageUrl"] as? String ?: data["avatarUrl"] as? String
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this).load(imageUrl).placeholder(CommonR.drawable.ic_person_placeholder).into(imgStudent)
        }

        val lat = data["latitude"] as? Double
        val lng = data["longitude"] as? Double
        if (lat != null && lng != null && ::mapPreview.isInitialized) {
            val point = GeoPoint(lat, lng)
            mapPreview.controller.setCenter(point)
            mapPreview.overlays.clear()
            val marker = Marker(mapPreview)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapPreview.overlays.add(marker)
            mapPreview.invalidate()
        }
    }

    private fun showEditGeneralDialog() {
        val context = context ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_student_general, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val imgAvatar = dialogView.findViewById<ImageView>(R.id.imgEditStudentAvatar)
        imgEditAvatar = imgAvatar
        val btnChangeAvatar = dialogView.findViewById<View>(R.id.rlEditStudentAvatar)
        val etId = dialogView.findViewById<EditText>(R.id.etEditStudentId)
        val etFirst = dialogView.findViewById<EditText>(R.id.etEditFirstName)
        val etLast = dialogView.findViewById<EditText>(R.id.etEditLastName)
        val etMiddle = dialogView.findViewById<EditText>(R.id.etEditMiddleName)
        val tvSuffix = dialogView.findViewById<TextView>(R.id.tvEditStudentSelectedSuffix)
        val btnSuffix = dialogView.findViewById<FrameLayout>(R.id.btnEditStudentSuffix)
        val etDob = dialogView.findViewById<EditText>(R.id.etEditDob)
        val etClass = dialogView.findViewById<EditText>(R.id.etEditClass)
        val tvGrade = dialogView.findViewById<TextView>(R.id.tvEditSelectedGrade)
        val btnGrade = dialogView.findViewById<FrameLayout>(R.id.btnEditGrade)
        val etSchool = dialogView.findViewById<EditText>(R.id.etEditSchool)
        val etAddress = dialogView.findViewById<EditText>(R.id.etEditAddress)
        val btnChangeLocation = dialogView.findViewById<Button>(R.id.btnRequestAddressEdit)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveStudent)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEdit)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnDismissEditGeneral)
        
        val llPending = dialogView.findViewById<LinearLayout>(R.id.llPendingRequest)
        val btnShowPending = dialogView.findViewById<MaterialButton>(R.id.btnShowPendingDetails)
        val llPendingDetails = dialogView.findViewById<LinearLayout>(R.id.llPendingDetails)
        val etPendingAddress = dialogView.findViewById<EditText>(R.id.etPendingAddress)
        val mapPending = dialogView.findViewById<MapView>(R.id.mapPendingPickup)

        studentData?.let { data ->
            etId.setText(data["studentId"] as? String)
            etFirst.setText(data["firstName"] as? String)
            etLast.setText(data["lastName"] as? String)
            etMiddle.setText(data["middleName"] as? String)
            tvSuffix.text = data["suffix"] as? String ?: "Suffix"
            etDob.setText(data["dob"] as? String)
            etClass.setText(data["class"] as? String)
            tvGrade.text = data["grade"] as? String ?: "Grade"
            etSchool.setText(data["school"] as? String)
            etAddress.setText(data["homeAddress"] as? String)

            val imageUrl = data["imageUrl"] as? String ?: data["avatarUrl"] as? String
            if (!imageUrl.isNullOrEmpty()) Glide.with(this).load(imageUrl).into(imgAvatar)
            
            val pendingAddress = data["pendingAddress"] as? String
            if (!pendingAddress.isNullOrEmpty()) {
                llPending.isVisible = true
                etPendingAddress.setText(pendingAddress)
                val pLat = data["pendingLatitude"] as? Double ?: 0.0
                val pLng = data["pendingLongitude"] as? Double ?: 0.0
                val pPoint = GeoPoint(pLat, pLng)
                mapPending.controller.setZoom(16.0)
                mapPending.controller.setCenter(pPoint)
                mapPending.overlays.clear()
                val pMarker = Marker(mapPending)
                pMarker.position = pPoint
                pMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapPending.overlays.add(pMarker)
            } else {
                llPending.isGone = true
            }
        }

        btnShowPending.setOnClickListener {
            if (llPendingDetails.isGone) {
                llPendingDetails.isVisible = true
                btnShowPending.setText(CommonR.string.hide_details)
            } else {
                llPendingDetails.isGone = true
                btnShowPending.setText(CommonR.string.show_details)
            }
        }

        btnSuffix.setOnClickListener {
            val suffixes = resources.getStringArray(CommonR.array.suffixes_array)
            AlertDialog.Builder(context).setTitle("Select Suffix").setItems(suffixes) { _, which -> tvSuffix.text = suffixes[which] }.show()
        }

        btnGrade.setOnClickListener {
            val grades = resources.getStringArray(CommonR.array.grades_array)
            AlertDialog.Builder(context).setTitle("Select Grade").setItems(grades) { _, which -> tvGrade.text = grades[which] }.show()
        }

        btnChangeAvatar.setOnClickListener { pickImage() }
        btnChangeLocation.setOnClickListener { dialog.dismiss(); showChangeLocationDialog() }

        btnSave.setOnClickListener {
            saveGeneralDetails(dialog, etId.text.toString(), etFirst.text.toString(), etLast.text.toString(), etMiddle.text.toString(),
                tvSuffix.text.toString(), etDob.text.toString(), etClass.text.toString(), tvGrade.text.toString(), etSchool.text.toString())
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showChangeLocationDialog() {
        val context = context ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_pickup_location, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        val etConfirmAddress = dialogView.findViewById<EditText>(R.id.etConfirmPickupAddress)
        val mapView = dialogView.findViewById<MapView>(R.id.mapConfirmPickup)
        val btnMyLocation = dialogView.findViewById<ImageButton>(R.id.btnConfirmMyLocation)
        val btnBack = dialogView.findViewById<Button>(R.id.btnConfirmBack)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmFinal)
        val flLoading = dialogView.findViewById<FrameLayout>(R.id.flConfirmLoading)

        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)

        val lat = studentData?.get("latitude") as? Double
        val lng = studentData?.get("longitude") as? Double
        val startPoint = if (lat != null && lng != null) {
            GeoPoint(lat, lng)
        } else { GeoPoint(14.6091, 121.0223) }
        mapView.controller.setCenter(startPoint)
        
        val marker = Marker(mapView)
        marker.position = startPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(marker)

        reverseGeocode(startPoint, etConfirmAddress)

        mapView.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                marker.position = p
                mapView.invalidate()
                reverseGeocode(p, etConfirmAddress)
                selectedGeoPoint = p
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))

        btnMyLocation.setOnClickListener {
            if (checkLocationPermission()) {
                LocationServices.getFusedLocationProviderClient(requireActivity()).lastLocation.addOnSuccessListener { location ->
                    if (location != null && isAdded) {
                        val userPoint = GeoPoint(location.latitude, location.longitude)
                        mapView.controller.animateTo(userPoint)
                        marker.position = userPoint
                        mapView.invalidate()
                        reverseGeocode(userPoint, etConfirmAddress)
                        selectedGeoPoint = userPoint
                    }
                }
            } else { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        }

        btnBack.setOnClickListener { dialog.dismiss(); showEditGeneralDialog() }

        btnConfirm.setOnClickListener {
            val newAddress = etConfirmAddress.text.toString()
            if (newAddress.isEmpty() || selectedGeoPoint == null) {
                Toast.makeText(context, "Please select a valid location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            AlertDialog.Builder(context)
                .setTitle(CommonR.string.address_change_request_title)
                .setMessage(CommonR.string.address_change_request_message)
                .setPositiveButton(CommonR.string.continue_caps) { _, _ ->
                    flLoading?.isVisible = true
                    submitLocationChangeRequest(dialog, newAddress, selectedGeoPoint!!)
                }
                .setNegativeButton(CommonR.string.cancel_caps, null)
                .show()
        }
        dialog.show()
    }

    private fun submitLocationChangeRequest(dialog: AlertDialog, address: String, point: GeoPoint) {
        val parentId = parentId ?: return
        val updates = hashMapOf<String, Any>(
            "pendingAddress" to address, 
            "pendingLatitude" to point.latitude, 
            "pendingLongitude" to point.longitude, 
            "addressChangeStatus" to "pending"
        )
        
        val task = if (studentId != null) {
            db.collection("parents").document(parentId).collection("students").document(studentId!!).update(updates)
        } else {
            db.collection("parents").document(parentId).update(updates)
        }

        task.addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                dialog.findViewById<FrameLayout>(R.id.flConfirmLoading)?.isGone = true
                
                val context = context ?: return@addOnSuccessListener
                AlertDialog.Builder(context)
                    .setTitle("Success")
                    .setMessage("Your request has been successfully submitted! Please wait for admin approval. Pickup location changes are typically processed during the weekly schedule update.")
                    .setPositiveButton("Okay") { successDialog, _ ->
                        successDialog.dismiss()
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                dialog.findViewById<FrameLayout>(R.id.flConfirmLoading)?.isGone = true
                Toast.makeText(context, "Failed to submit request", Toast.LENGTH_SHORT).show()
            }
    }

    private fun reverseGeocode(point: GeoPoint, editText: EditText) {
        val context = context ?: return
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                editText.setText(addresses[0].getAddressLine(0))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun checkLocationPermission(): Boolean = context?.let { 
        ContextCompat.checkSelfPermission(it, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED 
    } ?: false

    private fun pickImage() {
        val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data?.let { startCrop(it) }
    }

    private fun startCrop(uri: Uri) {
        val context = context ?: return
        val dest = Uri.fromFile(File(context.cacheDir, "cropped_${UUID.randomUUID()}.jpg"))
        val options = UCrop.Options().apply {
            setCompressionQuality(80)
            setToolbarColor(ContextCompat.getColor(context, CommonR.color.dark_header))
            setActiveControlsWidgetColor(ContextCompat.getColor(context, CommonR.color.yellow_primary))
        }
        UCrop.of(uri, dest).withAspectRatio(1f, 1f).withMaxResultSize(1000, 1000).withOptions(options).start(context, this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == android.app.Activity.RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            selectedImageUri = UCrop.getOutput(data!!)
            if (::imgEditAvatar.isInitialized) {
                imgEditAvatar.setImageURI(selectedImageUri)
            }
        }
    }

    private fun saveGeneralDetails(dialog: AlertDialog, id: String, first: String, last: String, middle: String, suffix: String, dob: String, clazz: String, grade: String, school: String) {
        val parentId = parentId ?: return
        val data = hashMapOf<String, Any>("studentId" to id, "firstName" to first, "lastName" to last, "middleName" to middle, "suffix" to if (suffix == "Suffix") "" else suffix, "dob" to dob, "class" to clazz, "grade" to if (grade == "Grade") "" else grade, "school" to school)
        
        val docRef = if (studentId != null) {
            db.collection("parents").document(parentId).collection("students").document(studentId!!)
        } else {
            db.collection("parents").document(parentId)
        }

        if (selectedImageUri != null) uploadImageAndSave(docRef as com.google.firebase.firestore.DocumentReference, data, dialog)
        else {
            if (studentId != null) {
                (docRef as com.google.firebase.firestore.DocumentReference).update(data).addOnSuccessListener { 
                    if (isAdded) {
                        Toast.makeText(context, "Details updated", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            } else {
                Toast.makeText(context, "Update not supported for this student format yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadImageAndSave(docRef: com.google.firebase.firestore.DocumentReference, data: HashMap<String, Any>, dialog: AlertDialog) {
        val fileName = "students/${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child(fileName)
        storageRef.putFile(selectedImageUri!!).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                data["imageUrl"] = uri.toString()
                docRef.update(data).addOnSuccessListener { 
                    if (isAdded) dialog.dismiss() 
                }
            }
        }.addOnFailureListener { 
            if (isAdded) Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show() 
        }
    }

    override fun onResume() { 
        super.onResume()
        if (::mapPreview.isInitialized) mapPreview.onResume() 
    }
    
    override fun onPause() { 
        super.onPause()
        if (::mapPreview.isInitialized) mapPreview.onPause() 
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        studentListener?.remove()
    }
}
