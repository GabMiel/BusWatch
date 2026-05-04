package com.example.buswatch.driver

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.example.buswatch.common.R as CommonR
import com.google.android.gms.location.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import androidx.preference.PreferenceManager
import com.onesignal.OneSignal
import java.io.File

class DriverHome : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val viewModel: DriverViewModel by viewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val ctx = applicationContext
        val config = Configuration.getInstance()
        config.userAgentValue = ctx.packageName
        config.osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        config.osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
        config.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        setContentView(R.layout.activity_driver_home)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Ensure OneSignal is linked to the current user
        auth.currentUser?.uid?.let { uid ->
            OneSignal.login(uid)
        }

        viewModel.fetchUserInfo()
        loadHome()
        startLocationUpdates()
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun loadHome() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }

    fun loadLiveTracking() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, TrackingFragment())
            .addToBackStack("tracking")
            .commit()
    }

    fun loadAccount() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AccountFragment())
            .addToBackStack(null)
            .commit()
    }

    fun loadSettings() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    fun showSOSConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sos_confirmation, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnCancelSOS)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnSendSOS)?.setOnClickListener {
            showSOSSendingState(dialog)
            sendEmergencyAlert(dialog)
        }
        dialog.show()
    }

    private fun showSOSSendingState(dialog: AlertDialog) {
        val sendingView = layoutInflater.inflate(R.layout.dialog_sos_sending, null)
        dialog.setContentView(sendingView)
        
        val pulseView = sendingView.findViewById<View>(R.id.pulseView)
        pulseView?.let {
            val anim = ScaleAnimation(0.8f, 1.4f, 0.8f, 1.4f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            anim.duration = 1000
            anim.repeatMode = Animation.REVERSE
            anim.repeatCount = Animation.INFINITE
            it.startAnimation(anim)
        }

        val dots = listOf(
            sendingView.findViewById<View>(R.id.dot1),
            sendingView.findViewById<View>(R.id.dot2),
            sendingView.findViewById<View>(R.id.dot3)
        )

        dots.forEachIndexed { index, dot ->
            dot?.let {
                val anim = ObjectAnimator.ofPropertyValuesHolder(
                    it,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -12f, 0f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 1f, 0.3f)
                )
                anim.duration = 800
                anim.startDelay = index * 150L
                anim.repeatCount = ObjectAnimator.INFINITE
                anim.start()
            }
        }
    }

    private fun sendEmergencyAlert(dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val lastLoc = viewModel.lastKnownLocation.value
        val route = viewModel.assignedRoute.value
        
        val emergencyData = hashMapOf(
            "driverId" to uid,
            "driverName" to (viewModel.userName.value ?: "Unknown Driver"),
            "routeId" to (route?.id ?: "N/A"),
            "routeName" to (route?.name ?: "Unknown Route"),
            "busNumber" to (route?.busNumber ?: "N/A"),
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "active",
            "type" to "SOS",
            "latitude" to (lastLoc?.latitude ?: 0.0),
            "longitude" to (lastLoc?.longitude ?: 0.0),
            "senderRole" to (viewModel.userRole.value ?: "Driver")
        )

        Handler(Looper.getMainLooper()).postDelayed({
            db.collection("emergencies").add(emergencyData)
                .addOnSuccessListener {
                    dialog.dismiss()
                    Toast.makeText(this, getString(CommonR.string.sos_sent_success), Toast.LENGTH_LONG).show()
                    viewModel.sendSOSNotification()
                }
                .addOnFailureListener { e ->
                    dialog.dismiss()
                    Toast.makeText(this, "Failed to send SOS: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }, 2000)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                val bearing = if (loc.hasBearing() && loc.speed > 0.5f) loc.bearing else 0f
                
                viewModel.setLocation(GeoPoint(loc.latitude, loc.longitude), bearing)
                
                val uid = auth.currentUser?.uid ?: return
                val locationData = mapOf(
                    "latitude" to loc.latitude,
                    "longitude" to loc.longitude,
                    "lastUpdated" to FieldValue.serverTimestamp()
                )
                
                val collection = if (viewModel.userRole.value == "Driver") "drivers" else "conductors"
                db.collection(collection).document(uid).update(locationData)
                
                viewModel.assignedRoute.value?.busId?.let { busId ->
                    db.collection("buses").document(busId).update(locationData)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }
}
