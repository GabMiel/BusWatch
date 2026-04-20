package com.example.buswatch.driver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import java.util.Calendar

class DriverHome : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var map: MapView? = null
    
    private var userRole: String = "Driver" 
    private var userName: String = ""
    private var assignedRouteId: String? = null
    private var assignedBusNumber: String? = null
    private var assignedRouteName: String? = null
    private var stopIds = mutableListOf<String>()
    
    private var studentListener: ListenerRegistration? = null
    private var isMapMaximized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val ctx = applicationContext
        val config = Configuration.getInstance()
        config.userAgentValue = ctx.packageName
        config.osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        config.osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
        config.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        fetchUserInfo()
    }

    private fun fetchUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        
        db.collection("drivers").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                userRole = "Driver"
                userName = doc.getString("firstName") ?: "Driver"
                fetchAssignedRoute(uid)
            } else {
                db.collection("conductors").document(uid).get().addOnSuccessListener { cDoc ->
                    if (cDoc.exists()) {
                        userRole = "Conductor"
                        userName = cDoc.getString("firstName") ?: "Conductor"
                        fetchAssignedRoute(uid)
                    } else {
                        loadHome()
                    }
                }
            }
        }.addOnFailureListener { loadHome() }
    }

    private fun fetchAssignedRoute(uid: String) {
        val field = if (userRole == "Driver") "driverId" else "conductorId"
        
        db.collection("routes")
            .whereEqualTo(field, uid)
            .whereEqualTo("status", "Active")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                if (!snapshots.isEmpty) {
                    val doc = snapshots.documents[0]
                    assignedRouteId = doc.id
                    assignedRouteName = doc.getString("routeName")
                    
                    @Suppress("UNCHECKED_CAST")
                    val fetchedStopIds = doc.get("stopIds") as? List<String>
                    if (fetchedStopIds != null) {
                        stopIds = fetchedStopIds.toMutableList()
                    }
                    
                    val busId = doc.getString("busId") ?: ""
                    if (busId.isNotEmpty()) {
                        db.collection("buses").document(busId).get().addOnSuccessListener { busDoc ->
                            assignedBusNumber = busDoc.getString("busNumber")
                            loadHome()
                        }
                    } else {
                        loadHome()
                    }
                } else {
                    loadHome()
                }
            }
    }

    private fun loadHome() {
        map = null
        setContentView(R.layout.fragment_driver_home)
        updateUI()
        setupRealTimeStudentList(R.id.recyclerStudents)
        setupBottomNav()
        
        findViewById<TextView>(R.id.btnStartTrip)?.setOnClickListener { loadLiveTracking() }
        findViewById<TextView>(R.id.tabAfternoon)?.setOnClickListener { loadAfternoon() }
    }

    private fun updateUI() {
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 0..11 -> getString(CommonR.string.good_morning)
            in 12..17 -> getString(CommonR.string.good_afternoon)
            else -> getString(CommonR.string.good_evening)
        }
        tvGreeting?.text = getString(CommonR.string.greeting_format, timeGreeting, userRole)

        val cardRoute = findViewById<View>(R.id.cardAssignedRoute)
        if (assignedRouteId != null) {
            cardRoute?.isVisible = true
            findViewById<TextView>(R.id.tvAssignedRouteName)?.text = assignedRouteName
            findViewById<TextView>(R.id.tvAssignedBus)?.text = getString(CommonR.string.bus_format, assignedBusNumber ?: "N/A")
        } else {
            cardRoute?.isVisible = false
        }

        val btnStart = findViewById<TextView>(R.id.btnStartTrip)
        if (userRole == "Conductor") {
            btnStart?.text = "OPEN STUDENT ROSTER"
        } else {
            btnStart?.text = getString(CommonR.string.start_trip)
        }
    }

    private fun setupRealTimeStudentList(recyclerViewId: Int) {
        val recyclerView = findViewById<RecyclerView>(recyclerViewId) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (stopIds.isEmpty()) {
            recyclerView.adapter = StudentAdapter(emptyList(), {}, {})
            return
        }

        studentListener?.remove()

        studentListener = db.collection("parents")
            .whereIn("child.stop", stopIds)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                
                val students = snapshots.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val child = doc.get("child") as? Map<String, Any>
                    val fName = child?.get("firstName") as? String ?: ""
                    val lName = child?.get("lastName") as? String ?: ""
                    val grade = child?.get("grade") as? String ?: "N/A"
                    val status = child?.get("status") as? String ?: getString(CommonR.string.status_at_home)
                    
                    Student(doc.id, "$fName $lName", grade, status)
                }
                
                recyclerView.adapter = StudentAdapter(students, 
                    onPickUpClick = { student -> updateStudentStatus(student.id, getString(CommonR.string.status_on_board)) },
                    onDropOffClick = { student -> updateStudentStatus(student.id, getString(CommonR.string.status_at_school)) }
                )

                if (recyclerViewId == R.id.recyclerPickup) {
                    val onBoardCount = students.count { it.status == getString(CommonR.string.status_on_board) }
                    val totalCount = students.size
                    findViewById<TextView>(R.id.tvBoardingCount)?.text = getString(CommonR.string.on_board_format, onBoardCount, totalCount)
                }
            }
    }

    private fun updateStudentStatus(parentId: String, newStatus: String) {
        db.collection("parents").document(parentId).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val child = doc.get("child") as? MutableMap<String, Any>
            if (child != null) {
                child["status"] = newStatus
                db.collection("parents").document(parentId).update("child", child)
            }
        }
    }

    private fun loadAfternoon() {
        map = null
        setContentView(R.layout.fragment_driver_afternoon)
        updateUI()
        setupRealTimeStudentList(R.id.recyclerStudents)
        setupBottomNav()

        findViewById<TextView>(R.id.tabMorning)?.setOnClickListener { loadHome() }
        findViewById<TextView>(R.id.btnStartTrip)?.setOnClickListener { loadLiveTracking() }
    }

    private fun loadLiveTracking() {
        setContentView(R.layout.fragment_live_tracking)
        isMapMaximized = false
        
        findViewById<ImageButton>(R.id.btnBackTracking)?.setOnClickListener { loadHome() }
        
        val btnEnd = findViewById<TextView>(R.id.btnEndTrip)
        val mapView = findViewById<View>(R.id.mapView)
        val layoutBottom = findViewById<View>(R.id.layoutBottomInfo)
        val btnMaximize = findViewById<ImageButton>(R.id.btnMaximizeMap)

        if (userRole == "Conductor") {
            // Conductor View: Hide Map, Expand Roster
            mapView?.visibility = View.GONE
            btnMaximize?.visibility = View.GONE
            btnEnd?.text = "EXIT ROSTER"
            btnEnd?.setOnClickListener { loadHome() }
            
            // Allow the recycler to expand since map is gone
            val recycler = findViewById<RecyclerView>(R.id.recyclerPickup)
            recycler?.layoutParams?.height = LinearLayout.LayoutParams.MATCH_PARENT
        } else {
            // Driver View: Show Map
            mapView?.visibility = View.VISIBLE
            btnMaximize?.visibility = View.VISIBLE
            btnEnd?.text = getString(CommonR.string.end_trip)
            btnEnd?.setOnClickListener { loadHome() }

            map = findViewById(R.id.mapView)
            map?.let { mv ->
                mv.setTileSource(TileSourceFactory.MAPNIK)
                mv.setMultiTouchControls(true)
                mv.controller.setZoom(15.0)
                mv.controller.setCenter(GeoPoint(14.5995, 120.9842))
            }

            btnMaximize?.setOnClickListener {
                isMapMaximized = !isMapMaximized
                layoutBottom?.isVisible = !isMapMaximized
                btnMaximize.setImageResource(if (isMapMaximized) CommonR.drawable.ic_close else CommonR.drawable.ic_eye)
            }
        }

        findViewById<TextView>(R.id.tvLiveTrackingTitle)?.text = getString(CommonR.string.live_trip_format, assignedRouteName ?: "Route")
        findViewById<TextView>(R.id.tvBusIndicator)?.text = getString(CommonR.string.bus_format, assignedBusNumber ?: "N/A")
        
        setupRealTimeStudentList(R.id.recyclerPickup)
    }

    private fun setupBottomNav() {
        val homeNav = findViewById<LinearLayout>(R.id.containerNavHome)
        val accountNav = findViewById<LinearLayout>(R.id.containerNavAccount)
        val settingsNav = findViewById<LinearLayout>(R.id.containerNavSettings)

        homeNav?.setOnClickListener { loadHome() }
        accountNav?.setOnClickListener { loadAccount() }
        settingsNav?.setOnClickListener { loadSettings() }
    }

    private fun loadAccount() {
        setContentView(R.layout.fragment_driver_account)
        setupBottomNav()
        
        val uid = auth.currentUser?.uid ?: return
        val collection = if (userRole == "Driver") "drivers" else "conductors"
        
        db.collection(collection).document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                findViewById<TextView>(R.id.tvProfileFirstName)?.text = doc.getString("firstName") ?: "-"
                findViewById<TextView>(R.id.tvProfileMiddleName)?.text = doc.getString("middleName") ?: "-"
                findViewById<TextView>(R.id.tvProfileLastName)?.text = doc.getString("lastName") ?: "-"
                findViewById<TextView>(R.id.tvProfileSuffix)?.text = doc.getString("suffix") ?: "-"
                findViewById<TextView>(R.id.tvProfileEmail)?.text = doc.getString("email") ?: "-"
                findViewById<TextView>(R.id.tvProfilePhone)?.text = doc.getString("phoneNumber") ?: "-"
                findViewById<TextView>(R.id.tvProfileLanguage)?.text = "English" // Default for now
            }
        }
    }

    private fun loadSettings() {
        setContentView(R.layout.fragment_driver_settings)
        setupBottomNav()
        findViewById<ImageButton>(R.id.btnBackSettings)?.setOnClickListener { loadHome() }
        findViewById<TextView>(R.id.btnLogout)?.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, Class.forName("com.example.buswatch.Login"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        studentListener?.remove()
    }

    override fun onResume() { super.onResume(); map?.onResume() }
    override fun onPause() { super.onPause(); map?.onPause() }
}
