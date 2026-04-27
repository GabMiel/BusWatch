package com.example.buswatch

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.collections.Map as KMap

class StudentDetailsGeneralFragment : Fragment() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var parentStatus: String = "pending"
    private var homePoint: GeoPoint? = null
    private var childName: String? = null
    
    private val defaultPoint = GeoPoint(14.6760, 121.0437)
    
    private var tempAvatarUri: Uri? = null
    private var dialogAvatarView: ImageView? = null
    
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permission granted
        }
    }

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            tempAvatarUri = it
            dialogAvatarView?.let { iv ->
                Glide.with(this).load(it).circleCrop().into(iv)
            }
        }
    }

    companion object {
        fun newInstance(childName: String?): StudentDetailsGeneralFragment {
            val fragment = StudentDetailsGeneralFragment()
            val args = Bundle()
            args.putString("childName", childName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = requireContext().applicationContext
        Configuration.getInstance().userAgentValue = ctx.packageName
        Configuration.getInstance().osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_student_details_general, container, false)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        childName = arguments?.getString("childName")
        
        setupUI(view)
        loadStudentAndParentData(view)
        
        return view
    }

    private fun setupUI(view: View) {
        view.findViewById<Button>(R.id.btnAssignStop)?.setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                showStopPickerDialog()
            } else {
                Toast.makeText(requireContext(), "Action restricted until account is approved.", Toast.LENGTH_SHORT).show()
            }
        }
        
        view.findViewById<ImageButton>(R.id.btnGeneralEdit)?.setOnClickListener {
            showEditStudentGeneralDialog()
        }
        
        view.findViewById<MapView>(R.id.mapHomeLocation)?.let { initMap(it, defaultPoint) }
        view.findViewById<MapView>(R.id.mapStopLocation)?.let { initMap(it, defaultPoint) }
    }

    private fun initMap(map: MapView, center: GeoPoint) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setTilesScaledToDpi(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(center)
    }

    private fun loadStudentAndParentData(view: View) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            if (doc.exists()) {
                parentStatus = doc.getString("status") ?: "pending"
                
                @Suppress("UNCHECKED_CAST")
                val childMap = doc.get("child") as? KMap<String, Any>
                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<KMap<String, Any>>
                
                var foundChild: KMap<String, Any>? = null
                
                if (childMap != null) {
                    val fullName = "${childMap["firstName"]} ${childMap["lastName"]}".trim()
                    if (childName == null || fullName == childName) {
                        foundChild = childMap
                    }
                }
                
                if (foundChild == null && childrenList != null) {
                    foundChild = childrenList.find { 
                        "${it["firstName"]} ${it["lastName"]}".trim() == childName 
                    }
                }

                foundChild?.let { childData ->
                    displayChildInfo(view, childData)
                    val currentStopId = childData["stop"] as? String
                    updateStopAndMapUI(view, currentStopId)
                }
            }
        }
    }

    private fun displayChildInfo(view: View, childData: KMap<String, Any>) {
        view.findViewById<TextView>(R.id.tvStudentName).text = getString(com.example.buswatch.common.R.string.name_format, childData["firstName"], childData["lastName"])
        view.findViewById<TextView>(R.id.tvDob).text = childData["age"]?.toString() ?: "-"
        view.findViewById<TextView>(R.id.tvSchool).text = childData["school"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvGrade).text = childData["grade"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvSection).text = childData["class"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvAddress).text = childData["address"] as? String ?: "-"
        
        val lat = childData["latitude"] as? Double ?: 0.0
        val lng = childData["longitude"] as? Double ?: 0.0
        
        if (lat != 0.0 && lng != 0.0) {
            homePoint = GeoPoint(lat, lng)
        } else {
            homePoint = null
        }

        val homeMap = view.findViewById<MapView>(R.id.mapHomeLocation)
        homePoint?.let { 
            setupMarkerOnMap(homeMap, it, "Home Location", com.example.buswatch.common.R.drawable.ic_location) 
        } ?: run {
            homeMap.controller.setCenter(defaultPoint)
        }

        val avatar = childData["childAvatarUrl"] as? String ?: childData["avatarUrl"] as? String ?: ""
        if (avatar.isNotEmpty()) {
            Glide.with(this).load(avatar).circleCrop().into(view.findViewById(R.id.imgStudentAvatar))
        } else {
            view.findViewById<ImageView>(R.id.imgStudentAvatar).setImageResource(com.example.buswatch.common.R.drawable.user)
        }
    }

    private fun updateStopAndMapUI(view: View, stopId: String?) {
        val tvStatus = view.findViewById<TextView>(R.id.tvAssignedStop)
        val btnAssign = view.findViewById<Button>(R.id.btnAssignStop)
        val stopMap = view.findViewById<MapView>(R.id.mapStopLocation)

        if (stopId.isNullOrEmpty()) {
            tvStatus?.setText(com.example.buswatch.common.R.string.pickup_stop_not_selected)
            btnAssign?.text = "SET PICKUP STOP"
            loadAllStopsOnMap(stopMap, null)
        } else {
            btnAssign?.text = "CHANGE PICKUP STOP"
            db.collection("stops").document(stopId).get().addOnSuccessListener { stopDoc ->
                if (!isAdded) return@addOnSuccessListener
                val stopName = stopDoc.getString("name") ?: "Selected Stop"
                tvStatus?.text = getString(com.example.buswatch.common.R.string.confirmed_stop_format, stopName)
                loadAllStopsOnMap(stopMap, stopId)
            }
        }
    }

    private fun loadAllStopsOnMap(map: MapView?, selectedStopId: String?) {
        if (map == null) return
        map.overlays.clear()
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setTilesScaledToDpi(true)

        val points = mutableListOf<GeoPoint>()
        homePoint?.let { points.add(it) }

        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            
            var selectedPoint: GeoPoint? = null
            
            for (doc in snapshots) {
                val point = GeoPoint(doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0)
                if (point.latitude == 0.0) continue
                
                val isSelected = doc.id == selectedStopId
                if (isSelected) selectedPoint = point
                
                val marker = Marker(map)
                marker.position = point
                marker.title = doc.getString("name")
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                val iconRes = if (isSelected) 
                    com.example.buswatch.common.R.drawable.ic_stop_marker_red 
                else 
                    com.example.buswatch.common.R.drawable.ic_stop_marker_blue
                
                val size = if (isSelected) 36 else 28
                marker.icon = getScaledDrawable(ContextCompat.getDrawable(requireContext(), iconRes), size, size)
                map.overlays.add(marker)
                points.add(point)
            }

            if (selectedPoint != null && homePoint != null) {
                val line = Polyline(map)
                line.setPoints(listOf(homePoint!!, selectedPoint))
                line.outlinePaint.color = Color.parseColor("#FEBE1E")
                line.outlinePaint.strokeWidth = 6f
                line.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
                map.overlays.add(0, line)
            }

            homePoint?.let { hp ->
                val homeMarker = Marker(map)
                homeMarker.position = hp
                homeMarker.title = "Home Location"
                homeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val hIcon = ContextCompat.getDrawable(requireContext(), com.example.buswatch.common.R.drawable.ic_location)
                homeMarker.icon = getScaledDrawable(hIcon, 32, 32)
                map.overlays.add(homeMarker)
            }

            if (points.isNotEmpty()) {
                val box = if (points.size > 1) {
                    BoundingBox.fromGeoPoints(points)
                } else {
                    val p = points[0]
                    BoundingBox(p.latitude + 0.005, p.longitude + 0.005, p.latitude - 0.005, p.longitude - 0.005)
                }
                map.post { map.zoomToBoundingBox(box, true, 120) }
            } else {
                map.controller.setCenter(defaultPoint)
            }
            map.invalidate()
        }
    }

    private fun getScaledDrawable(drawable: Drawable?, widthDp: Int, heightDp: Int): Drawable? {
        if (drawable == null) return null
        val density = resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    private fun setupMarkerOnMap(map: MapView?, point: GeoPoint, title: String, iconRes: Int) {
        if (map == null) return
        map.overlays.clear()
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setTilesScaledToDpi(true)
        map.controller.setZoom(17.0)
        map.controller.setCenter(point)
        
        val marker = Marker(map)
        marker.position = point
        marker.title = title
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        val icon = ContextCompat.getDrawable(requireContext(), iconRes)
        val size = if (iconRes == com.example.buswatch.common.R.drawable.ic_stop_marker_red) 36 else 32
        marker.icon = getScaledDrawable(icon, size, size)
        map.overlays.add(marker)
        map.invalidate()
    }

    @SuppressLint("MissingPermission")
    private fun showStopPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_pickup_stop, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()

        val map = dialogView.findViewById<MapView>(R.id.mapStopPicker)
        val tvSelectedStop = dialogView.findViewById<TextView>(R.id.tvSelectedStopName)
        val btnWholeView = dialogView.findViewById<ImageButton>(R.id.btnWholeView)
        val btnMyLocation = dialogView.findViewById<ImageButton>(R.id.btnMapMyLocation)
        
        var selectedStopId: String? = null
        var selectedStopName: String? = null
        val markers = mutableListOf<Marker>()

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setTilesScaledToDpi(true)
        map.controller.setZoom(15.0)
        
        val startPoint = homePoint ?: defaultPoint
        map.controller.setCenter(startPoint)

        homePoint?.let {
            val homeMarker = Marker(map)
            homeMarker.position = it
            homeMarker.title = "Home Location"
            homeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            val icon = ContextCompat.getDrawable(requireContext(), com.example.buswatch.common.R.drawable.ic_location)
            homeMarker.icon = getScaledDrawable(icon, 32, 32)
            map.overlays.add(homeMarker)
        }

        val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)
        myLocationOverlay.enableMyLocation()
        map.overlays.add(myLocationOverlay)

        map.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.performClick()
            }
            false
        }

        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            for (doc in snapshots) {
                val point = GeoPoint(doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0)
                if (point.latitude == 0.0) continue
                
                val name = doc.getString("name") ?: "Unknown"
                val marker = Marker(map)
                marker.position = point
                marker.title = name
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val icon = ContextCompat.getDrawable(requireContext(), com.example.buswatch.common.R.drawable.ic_stop_marker_blue)
                marker.icon = getScaledDrawable(icon, 32, 32)
                
                marker.setOnMarkerClickListener { m, _ ->
                    markers.forEach { otherMarker ->
                        val defaultIcon = ContextCompat.getDrawable(requireContext(), com.example.buswatch.common.R.drawable.ic_stop_marker_blue)
                        otherMarker.icon = getScaledDrawable(defaultIcon, 32, 32)
                    }
                    val selectedIcon = ContextCompat.getDrawable(requireContext(), com.example.buswatch.common.R.drawable.ic_stop_marker_red)
                    m.icon = getScaledDrawable(selectedIcon, 36, 36)
                    
                    selectedStopId = doc.id
                    selectedStopName = name
                    tvSelectedStop.text = name
                    tvSelectedStop.setTextColor(Color.BLACK)
                    
                    m.showInfoWindow()
                    map.controller.animateTo(m.position)
                    map.invalidate()
                    true
                }
                map.overlays.add(marker)
                markers.add(marker)
            }
            map.invalidate()
        }

        btnWholeView.setOnClickListener {
            val points = markers.map { it.position }.toMutableList()
            homePoint?.let { points.add(it) }
            if (points.isNotEmpty()) {
                val box = BoundingBox.fromGeoPoints(points)
                map.zoomToBoundingBox(box, true, 120)
            }
        }

        btnMyLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val currentPoint = GeoPoint(location.latitude, location.longitude)
                        map.controller.animateTo(currentPoint)
                        map.controller.setZoom(18.0)
                    } else {
                        Toast.makeText(requireContext(), "Unable to get current location", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        dialogView.findViewById<Button>(R.id.btnCancelStop).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnConfirmStop).setOnClickListener {
            val sId = selectedStopId
            val sName = selectedStopName
            if (sId != null && sName != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Selection")
                    .setMessage("Are you sure you want to set '$sName' as your pickup stop?")
                    .setPositiveButton("Yes") { _, _ ->
                        saveStopSelection(sId, sName)
                        dialog.dismiss()
                    }
                    .setNegativeButton("No", null)
                    .show()
            } else {
                Toast.makeText(requireContext(), "Please select a stop from the map", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun saveStopSelection(stopId: String, stopName: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            @Suppress("UNCHECKED_CAST")
            val childMap = doc.get("child") as? KMap<String, Any>
            @Suppress("UNCHECKED_CAST")
            val childrenList = doc.get("children") as? List<KMap<String, Any>>
            
            if (childMap != null) {
                val fullName = "${childMap["firstName"]} ${childMap["lastName"]}".trim()
                if (childName == null || fullName == childName) {
                    val updatedChild = childMap.toMutableMap()
                    updatedChild["stop"] = stopId
                    db.collection("parents").document(uid).update("child", updatedChild).addOnSuccessListener {
                        if (!isAdded) return@addOnSuccessListener
                        Toast.makeText(requireContext(), getString(com.example.buswatch.common.R.string.confirmed_stop_format, stopName), Toast.LENGTH_SHORT).show()
                        updateStopAndMapUI(requireView(), stopId)
                    }
                    return@addOnSuccessListener
                }
            }
            
            if (childrenList != null) {
                val newList = childrenList.toMutableList()
                val index = newList.indexOfFirst { 
                    "${it["firstName"]} ${it["lastName"]}".trim() == childName 
                }
                if (index != -1) {
                    val updatedChild = newList[index].toMutableMap()
                    updatedChild["stop"] = stopId
                    newList[index] = updatedChild
                    db.collection("parents").document(uid).update("children", newList).addOnSuccessListener {
                        if (!isAdded) return@addOnSuccessListener
                        Toast.makeText(requireContext(), getString(com.example.buswatch.common.R.string.confirmed_stop_format, stopName), Toast.LENGTH_SHORT).show()
                        updateStopAndMapUI(requireView(), stopId)
                    }
                }
            }
        }
    }

    private fun showEditStudentGeneralDialog() {
        val uid = auth.currentUser?.uid ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_student_general, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()

        tempAvatarUri = null
        dialogAvatarView = dialogView.findViewById(R.id.imgEditStudentAvatar)

        val etFirstName = dialogView.findViewById<EditText>(R.id.etEditFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etEditLastName)
        val etMiddleName = dialogView.findViewById<EditText>(R.id.etEditMiddleName)
        val etAge = dialogView.findViewById<EditText>(R.id.etEditDob)
        val etSection = dialogView.findViewById<EditText>(R.id.etEditSection)
        val etSchool = dialogView.findViewById<EditText>(R.id.etEditSchool)
        val etAddress = dialogView.findViewById<EditText>(R.id.etEditAddress)
        
        val tvSelectedSuffix = dialogView.findViewById<TextView>(R.id.tvEditStudentSelectedSuffix)
        var selectedSuffix = ""
        val tvSelectedGrade = dialogView.findViewById<TextView>(R.id.tvEditSelectedGrade)
        var selectedGrade = ""

        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            @Suppress("UNCHECKED_CAST")
            val childMap = doc.get("child") as? KMap<String, Any>
            @Suppress("UNCHECKED_CAST")
            val childrenList = doc.get("children") as? List<KMap<String, Any>>
            
            var targetChild: KMap<String, Any>? = null
            if (childMap != null) {
                val fullName = "${childMap["firstName"]} ${childMap["lastName"]}".trim()
                if (childName == null || fullName == childName) targetChild = childMap
            }
            if (targetChild == null && childrenList != null) {
                targetChild = childrenList.find { "${it["firstName"]} ${it["lastName"]}".trim() == childName }
            }

            targetChild?.let { child ->
                etFirstName.setText(child["firstName"] as? String ?: "")
                etLastName.setText(child["lastName"] as? String ?: "")
                etMiddleName.setText(child["middleName"] as? String ?: "")
                etAge.setText(child["age"]?.toString() ?: "")
                etSection.setText(child["class"] as? String ?: child["section"] as? String ?: "")
                etSchool.setText(child["school"] as? String ?: "")
                etAddress.setText(child["address"] as? String ?: "")
                
                selectedSuffix = child["suffix"] as? String ?: ""
                tvSelectedSuffix.text = if (selectedSuffix.isEmpty()) getString(com.example.buswatch.common.R.string.suffix) else selectedSuffix
                
                selectedGrade = child["grade"] as? String ?: ""
                tvSelectedGrade.text = if (selectedGrade.isEmpty()) getString(com.example.buswatch.common.R.string.grade) else selectedGrade
                
                val avatar = child["childAvatarUrl"] as? String ?: child["avatarUrl"] as? String ?: ""
                if (avatar.isNotEmpty()) {
                    Glide.with(this).load(avatar).circleCrop().into(dialogAvatarView!!)
                }
                
                val stopId = child["stop"] as? String ?: ""
                val tvStop = dialogView.findViewById<TextView>(R.id.tvEditAssignedStop)
                if (stopId.isNotEmpty()) {
                    db.collection("stops").document(stopId).get().addOnSuccessListener { sDoc ->
                        if (!isAdded) return@addOnSuccessListener
                        tvStop.text = sDoc.getString("name") ?: stopId
                    }
                }
                
                val editMap = dialogView.findViewById<MapView>(R.id.mapEditHome)
                homePoint?.let { 
                    setupMarkerOnMap(editMap, it, "Home Location", com.example.buswatch.common.R.drawable.ic_location) 
                } ?: run {
                    editMap.controller.setCenter(defaultPoint)
                }
            }
        }

        dialogView.findViewById<FrameLayout>(R.id.btnEditStudentSuffix).setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(requireContext()).setItems(suffixes) { _, pos ->
                selectedSuffix = if (pos == 0) "" else suffixes[pos]
                tvSelectedSuffix.text = if (pos == 0) getString(com.example.buswatch.common.R.string.suffix) else suffixes[pos]
            }.show()
        }

        dialogView.findViewById<FrameLayout>(R.id.btnEditGrade).setOnClickListener {
            val grades = resources.getStringArray(com.example.buswatch.common.R.array.grades_array)
            AlertDialog.Builder(requireContext()).setItems(grades) { _, pos ->
                selectedGrade = grades[pos]
                tvSelectedGrade.text = selectedGrade
            }.show()
        }

        dialogView.findViewById<Button>(R.id.btnEditChangeStop).setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                Toast.makeText(requireContext(), "Close this dialog and use the 'Set Pickup Stop' button on the profile page for stop changes.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Stop assignment is restricted until account is approved.", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<View>(R.id.rlEditStudentAvatar).setOnClickListener { pickAvatarLauncher.launch("image/*") }
        dialogView.findViewById<ImageButton>(R.id.btnDismissEditGeneral).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener { dialog.dismiss() }
        
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveStudent)
        btnSave.setOnClickListener {
            btnSave.isEnabled = false
            val updatedData = mutableMapOf<String, Any>(
                "firstName" to etFirstName.text.toString().trim(),
                "lastName" to etLastName.text.toString().trim(),
                "middleName" to etMiddleName.text.toString().trim(),
                "suffix" to selectedSuffix,
                "age" to etAge.text.toString().trim(),
                "class" to etSection.text.toString().trim(),
                "school" to etSchool.text.toString().trim(),
                "address" to etAddress.text.toString().trim(),
                "grade" to selectedGrade
            )

            if (tempAvatarUri != null) {
                uploadChildAvatarAndSave(uid, tempAvatarUri!!, updatedData, dialog)
            } else {
                saveStudentData(uid, updatedData, dialog)
            }
        }
        dialog.show()
    }

    private fun uploadChildAvatarAndSave(uid: String, uri: Uri, data: MutableMap<String, Any>, dialog: AlertDialog) {
        val ts = System.currentTimeMillis()
        val publicId = "child_update_${ts}"
        
        MediaManager.get().upload(uri)
            .unsigned("buswatch_unsigned")
            .option("folder", "parents/$uid")
            .option("public_id", publicId)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: MutableMap<Any?, Any?>?) {
                    data["childAvatarUrl"] = resultData?.get("secure_url") as? String ?: ""
                    saveStudentData(uid, data, dialog)
                }
                override fun onError(requestId: String, error: ErrorInfo?) {
                    if (!isAdded) return
                    Toast.makeText(requireContext(), "Upload failed", Toast.LENGTH_SHORT).show()
                    dialog.findViewById<Button>(R.id.btnSaveStudent)?.isEnabled = true
                }
                override fun onReschedule(requestId: String, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveStudentData(uid: String, updatedChild: MutableMap<String, Any>, dialog: AlertDialog) {
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            @Suppress("UNCHECKED_CAST")
            val childMap = doc.get("child") as? KMap<String, Any>
            @Suppress("UNCHECKED_CAST")
            val childrenList = doc.get("children") as? List<KMap<String, Any>>
            
            if (childMap != null) {
                val fullName = "${childMap["firstName"]} ${childMap["lastName"]}".trim()
                if (childName == null || fullName == childName) {
                    val finalChild = childMap.toMutableMap()
                    finalChild.putAll(updatedChild)
                    db.collection("parents").document(uid).update("child", finalChild).addOnSuccessListener {
                        if (!isAdded) return@addOnSuccessListener
                        Toast.makeText(requireContext(), "Updated successfully", Toast.LENGTH_SHORT).show()
                        childName = "${finalChild["firstName"]} ${finalChild["lastName"]}".trim()
                        loadStudentAndParentData(requireView())
                        dialog.dismiss()
                    }
                    return@addOnSuccessListener
                }
            }
            
            if (childrenList != null) {
                val newList = childrenList.toMutableList()
                val index = newList.indexOfFirst { "${it["firstName"]} ${it["lastName"]}".trim() == childName }
                if (index != -1) {
                    val finalChild = newList[index].toMutableMap()
                    finalChild.putAll(updatedChild)
                    newList[index] = finalChild
                    db.collection("parents").document(uid).update("children", newList).addOnSuccessListener {
                        if (!isAdded) return@addOnSuccessListener
                        Toast.makeText(requireContext(), "Updated successfully", Toast.LENGTH_SHORT).show()
                        childName = "${finalChild["firstName"]} ${finalChild["lastName"]}".trim()
                        loadStudentAndParentData(requireView())
                        dialog.dismiss()
                    }
                }
            }
        }
    }
}
