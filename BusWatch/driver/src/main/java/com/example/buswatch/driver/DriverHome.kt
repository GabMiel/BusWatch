package com.example.buswatch.driver

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.buswatch.common.R as CommonR
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private var notificationListener: ListenerRegistration? = null
    private var currentTabTag: String = "HOME"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val ctx = applicationContext
        val config = Configuration.getInstance()
        config.userAgentValue = "${ctx.packageName} (BusWatch Android App; support@buswatch.com)"
        config.osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        config.osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
        config.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        setContentView(R.layout.activity_driver_home)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        
        auth.currentUser?.uid?.let { uid ->
            OneSignal.login(uid)
        }

        viewModel.fetchUserInfo()
        
        // Initial fragment load
        val tab = viewModel.currentTab.value ?: "Morning"
        if (tab == "Morning") loadHome() else loadAfternoon()
        
        startLocationUpdates()
        checkNotificationPermission()
        setupSharedObservers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabTag != "HOME") {
                    loadHome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        startNotificationListener()
    }

    override fun onStop() {
        super.onStop()
        stopNotificationListener()
    }

    private fun setupSharedObservers() {
        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearToast()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startNotificationListener() {
        val uid = auth.currentUser?.uid ?: return
        val role = viewModel.userRole.value ?: "drivers"
        val collection = if (role == "Driver") "drivers" else "conductors"
        
        var isInitialSnapshot = true
        stopNotificationListener()

        notificationListener = db.collection(collection).document(uid)
            .collection("notifications")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) {
                    isInitialSnapshot = false
                    return@addSnapshotListener
                }
                
                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        if (!isInitialSnapshot) {
                            val doc = dc.document
                            if (currentTabTag != "NOTIFICATION") {
                                val title = doc.getString("title") ?: "BusWatch Update"
                                val message = doc.getString("message") ?: ""
                                showSystemNotification(title, message)
                            }
                        }
                    }
                }
                isInitialSnapshot = false
            }
    }

    private fun stopNotificationListener() {
        notificationListener?.remove()
        notificationListener = null
    }

    private fun showSystemNotification(title: String, message: String) {
        val intent = Intent(this, DriverHome::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "BUSWATCH_NOTIF")
            .setSmallIcon(CommonR.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } else {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    fun loadHome() {
        currentTabTag = "HOME"
        viewModel.setCurrentTab("Morning")
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }

    fun loadAfternoon() {
        currentTabTag = "HOME"
        viewModel.setCurrentTab("Afternoon")
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AfternoonFragment())
            .commit()
    }

    fun loadLiveTracking() {
        currentTabTag = "TRACKING"
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, TrackingFragment())
            .addToBackStack("tracking")
            .commit()
    }

    fun loadAccount() {
        currentTabTag = "ACCOUNT"
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AccountFragment())
            .addToBackStack(null)
            .commit()
    }

    fun loadSettings() {
        currentTabTag = "SETTINGS"
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    fun showSOSConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sos_confirmation, findViewById(android.R.id.content), false)
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
        val sendingView = layoutInflater.inflate(R.layout.dialog_sos_sending, findViewById(android.R.id.content), false)
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
                .addOnFailureListener { _ ->
                    dialog.dismiss()
                    Toast.makeText(this, "Failed to send SOS", Toast.LENGTH_LONG).show()
                }
        }, 2000)
    }

    private fun startLocationUpdates() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
            return
        }

        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(this)
        if (result != ConnectionResult.SUCCESS) {
            if (availability.isUserResolvableError(result)) {
                availability.getErrorDialog(this, result, 9000)?.show()
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .setWaitForAccurateLocation(false)
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
                
                val role = viewModel.userRole.value ?: "Driver"
                val collection = if (role == "Driver") "drivers" else "conductors"
                db.collection(collection).document(uid).update(locationData)
                
                viewModel.assignedRoute.value?.busId?.let { busId ->
                    db.collection("buses").document(busId).update(locationData)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("DriverHome", "SecurityException requesting location: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        stopNotificationListener()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Precise location is required for tracking", Toast.LENGTH_LONG).show()
            }
        }
    }
}
