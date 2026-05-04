package com.example.buswatch

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.buswatch.common.R as CommonR
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class ConfirmPickupLocationDialogFragment : DialogFragment() {

    private lateinit var viewModel: StudentViewModel
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var childName: String? = null
    private var currentAddress: String = ""
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    
    private var isMaximized = false
    private var marker: Marker? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            moveToCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance(childName: String?, address: String, lat: Double, lng: Double): ConfirmPickupLocationDialogFragment {
            val fragment = ConfirmPickupLocationDialogFragment()
            val args = Bundle()
            args.putString("childName", childName)
            args.putString("address", address)
            args.putDouble("lat", lat)
            args.putDouble("lng", lng)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set as a normal dialog with no title
        setStyle(STYLE_NO_TITLE, 0)
        viewModel = ViewModelProvider(requireParentFragment()).get(StudentViewModel::class.java)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        arguments?.let {
            childName = it.getString("childName")
            currentAddress = it.getString("address") ?: ""
            currentLat = it.getDouble("lat")
            currentLng = it.getDouble("lng")
            selectedLat = currentLat
            selectedLng = currentLng
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            if (!isMaximized) {
                // Set width to 90% of screen and wrap content for height
                val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            } else {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_confirm_pickup_location, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val map = view.findViewById<MapView>(R.id.mapConfirmHome)
        val etAddress = view.findViewById<EditText>(R.id.etConfirmHomeAddress)
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnConfirmMaximize)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnConfirmMyLocation)
        val btnBack = view.findViewById<Button>(R.id.btnConfirmBack)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmFinal)
        
        etAddress.setText(currentAddress)

        map.setMultiTouchControls(true)
        map.controller.setZoom(17.5)
        val startPoint = if (currentLat != 0.0) GeoPoint(currentLat, currentLng) else GeoPoint(14.5995, 120.9842)
        map.controller.setCenter(startPoint)

        marker = Marker(map)
        marker?.position = startPoint
        marker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker?.icon = MapUtils.getScaledDrawable(requireContext(), CommonR.drawable.ic_location, 32, 32)
        marker?.isDraggable = true
        marker?.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDrag(m: Marker?) {}
            override fun onMarkerDragEnd(m: Marker?) {
                m?.position?.let {
                    selectedLat = it.latitude
                    selectedLng = it.longitude
                    updateAddressFromLocation(it.latitude, it.longitude, etAddress)
                }
            }
            override fun onMarkerDragStart(m: Marker?) {}
        })
        map.overlays.add(marker)

        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    selectedLat = it.latitude
                    selectedLng = it.longitude
                    marker?.position = it
                    map.invalidate()
                    updateAddressFromLocation(it.latitude, it.longitude, etAddress)
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        map.overlays.add(0, eventsOverlay)

        btnMaximize.setOnClickListener {
            isMaximized = !isMaximized
            toggleMaximize(view, btnMaximize)
        }

        btnMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }

        btnBack.setOnClickListener { dismiss() }

        btnConfirm.setOnClickListener {
            showConfirmationPopup(etAddress.text.toString())
        }
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val gp = GeoPoint(it.latitude, it.longitude)
                val map = view?.findViewById<MapView>(R.id.mapConfirmHome)
                val etAddress = view?.findViewById<EditText>(R.id.etConfirmHomeAddress)

                selectedLat = it.latitude
                selectedLng = it.longitude

                marker?.position = gp
                map?.controller?.animateTo(gp)
                map?.invalidate()

                if (etAddress != null) {
                    updateAddressFromLocation(it.latitude, it.longitude, etAddress)
                }
            } ?: run {
                Toast.makeText(requireContext(), "Could not get current location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAddressFromLocation(lat: Double, lng: Double, etAddress: EditText) {
        if (!isAdded) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0].getAddressLine(0)
                    withContext(Dispatchers.Main) {
                        etAddress.setText(address)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleMaximize(view: View, btnMaximize: ImageButton) {
        val visibility = if (isMaximized) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.tvConfirmHomeTitle).visibility = visibility
        view.findViewById<View>(R.id.etConfirmHomeAddress).visibility = visibility
        view.findViewById<View>(R.id.tvPinInstruction).visibility = visibility
        view.findViewById<View>(R.id.llConfirmActions).visibility = visibility
        
        val cvMap = view.findViewById<View>(R.id.cvConfirmMap)
        val params = cvMap.layoutParams as ViewGroup.MarginLayoutParams
        if (isMaximized) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.topMargin = 0
            btnMaximize.setImageResource(CommonR.drawable.ic_close)
            dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            params.height = (250 * resources.displayMetrics.density).toInt()
            params.topMargin = (16 * resources.displayMetrics.density).toInt()
            btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
            dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        cvMap.layoutParams = params
    }

    private fun showConfirmationPopup(newAddress: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmation")
            .setMessage("Are you sure you want to change home address?")
            .setPositiveButton("CONFIRM") { _, _ ->
                submitRequest(newAddress)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun submitRequest(newAddress: String) {
        val uid = auth.currentUser?.uid ?: return
        val request = hashMapOf(
            "parentId" to uid,
            "studentName" to (childName ?: ""),
            "currentAddress" to currentAddress,
            "currentLat" to currentLat,
            "currentLng" to currentLng,
            "pendingAddress" to newAddress,
            "pendingLat" to selectedLat,
            "pendingLng" to selectedLng,
            "status" to "pending",
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        
        db.collection("map_requests").add(request).addOnSuccessListener {
            Toast.makeText(requireContext(), "Request submitted for approval", Toast.LENGTH_LONG).show()
            dismiss()
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to submit request", Toast.LENGTH_SHORT).show()
        }
    }
}
