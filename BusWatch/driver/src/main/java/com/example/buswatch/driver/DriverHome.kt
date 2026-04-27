package com.example.buswatch.driver

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
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

    private var morningTimeStr: String = ""
    private var afternoonTimeStr: String = ""
    
    private var studentListener: ListenerRegistration? = null
    private var isMapMaximized = false
    private val allMarkers = mutableMapOf<String, Marker>()
    private var driverMarker: Marker? = null
    private var currentTab: String = "Morning" // Default tab

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

                    val ms = doc.getString("morningStartTime") ?: ""
                    val me = doc.getString("morningEndTime") ?: ""
                    val `as` = doc.getString("afternoonStartTime") ?: ""
                    val ae = doc.getString("afternoonEndTime") ?: ""
                    
                    morningTimeStr = if (ms.isNotEmpty() && me.isNotEmpty()) "$ms - $me" else "No Schedule"
                    afternoonTimeStr = if (`as`.isNotEmpty() && ae.isNotEmpty()) "$`as` - $ae" else "No Schedule"
                    
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
        currentTab = "Morning"
        setContentView(R.layout.fragment_driver_home)
        updateUI()
        setupRealTimeStudentList(R.id.recyclerStudents)
        setupBottomNav()
        
        findViewById<TextView>(R.id.btnStartTrip)?.setOnClickListener { 
            if (userRole == "Conductor") {
                loadLiveTracking()
            } else {
                sendTripStartNotification()
                loadLiveTracking() 
            }
        }
        
        findViewById<TextView>(R.id.tabMorning)?.setOnClickListener { 
            currentTab = "Morning"
            updateTabUI(true)
            setupRealTimeStudentList(R.id.recyclerStudents)
        }
        findViewById<TextView>(R.id.tabAfternoon)?.setOnClickListener { 
            currentTab = "Afternoon"
            updateTabUI(false)
            setupRealTimeStudentList(R.id.recyclerStudents)
        }
    }

    private fun updateTabUI(isMorning: Boolean) {
        val tabMorning = findViewById<TextView>(R.id.tabMorning)
        val tabAfternoon = findViewById<TextView>(R.id.tabAfternoon)
        val tvShift = findViewById<TextView>(R.id.tvShiftTime)
        
        if (isMorning) {
            tabMorning?.setBackgroundResource(CommonR.drawable.bg_tab_active)
            tabMorning?.setTextColor(Color.BLACK)
            tabAfternoon?.setBackgroundResource(CommonR.drawable.bg_tab_inactive)
            tabAfternoon?.setTextColor(ContextCompat.getColor(this, CommonR.color.gray_text))
            tvShift?.text = morningTimeStr
        } else {
            tabMorning?.setBackgroundResource(CommonR.drawable.bg_tab_inactive)
            tabMorning?.setTextColor(ContextCompat.getColor(this, CommonR.color.gray_text))
            tabAfternoon?.setBackgroundResource(CommonR.drawable.bg_tab_active)
            tabAfternoon?.setTextColor(Color.BLACK)
            tvShift?.text = afternoonTimeStr
        }
    }

    private fun sendTripStartNotification() {
        if (stopIds.isEmpty()) return
        
        db.collection("parents")
            .whereIn("child.stop", stopIds)
            .get()
            .addOnSuccessListener { snapshots ->
                for (doc in snapshots) {
                    val parentId = doc.id
                    val notifData = hashMapOf(
                        "title" to "Bus Trip Started",
                        "message" to "The bus for ${assignedRouteName ?: "your route"} has started its trip. Be ready at your stop!",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "isRead" to false,
                        "type" to "trip_start"
                    )
                    db.collection("parents").document(parentId)
                        .collection("notifications").add(notifData)
                }
            }
    }

    private fun updateUI() {
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvCurrentTime = findViewById<TextView>(R.id.tvCurrentTime)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        
        val timeGreeting = when (hour) {
            in 0..11 -> getString(CommonR.string.good_morning)
            in 12..17 -> getString(CommonR.string.good_afternoon)
            else -> getString(CommonR.string.good_evening)
        }
        tvGreeting?.text = getString(CommonR.string.greeting_format, timeGreeting, userName)
        tvCurrentTime?.text = String.format(java.util.Locale.getDefault(), "%d:%02d %s", if (hour % 12 == 0) 12 else hour % 12, minute, amPm)

        findViewById<TextView>(R.id.tvShiftTime)?.text = if (currentTab == "Morning") morningTimeStr else afternoonTimeStr

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
            recyclerView.adapter = StudentAdapter(emptyList(), currentTab, {}, {})
            return
        }

        studentListener?.remove()

        val queryStops = if (stopIds.size > 30) stopIds.take(30) else stopIds

        studentListener = db.collection("parents")
            .whereIn("child.stop", queryStops)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                
                val students = snapshots.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val child = doc.get("child") as? Map<String, Any> ?: return@mapNotNull null
                    val fName = child["firstName"] as? String ?: ""
                    val lName = child["lastName"] as? String ?: ""
                    val grade = child["grade"] as? String ?: "N/A"
                    val status = child["status"] as? String ?: getString(CommonR.string.status_at_home)
                    val photoUrl = child["childAvatarUrl"] as? String ?: ""
                    val rideOption = child["rideOption"] as? String ?: "Morning Trip"
                    
                    // Show all students assigned to these stops
                    Student(doc.id, "$fName $lName", grade, status, photoUrl, rideOption)
                }
                
                // Pass currentTab to adapter so it can highlight cards
                recyclerView.adapter = StudentAdapter(students, currentTab,
                    onPickUpClick = { student -> 
                        updateStudentStatus(student.id, getString(CommonR.string.status_on_board))
                        sendStudentBoardingNotification(student.id, student.name)
                    },
                    onDropOffClick = { student -> updateStudentStatus(student.id, getString(CommonR.string.status_at_school)) }
                )

                if (recyclerViewId == R.id.recyclerPickup) {
                    val onBoardCount = students.count { it.status == getString(CommonR.string.status_on_board) }
                    val totalCount = students.count { s ->
                        when (currentTab) {
                            "Morning" -> s.rideOption == "Morning Trip"
                            "Afternoon" -> s.rideOption == "Afternoon Trip"
                            else -> true
                        }
                    }
                    findViewById<TextView>(R.id.tvBoardingCount)?.text = getString(CommonR.string.on_board_format, onBoardCount, totalCount)
                }
            }
    }

    private fun sendStudentBoardingNotification(parentId: String, studentName: String) {
        val notifData = hashMapOf(
            "title" to "Child Boarded",
            "message" to "$studentName has successfully boarded the bus.",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "type" to "student_boarding"
        )
        db.collection("parents").document(parentId).collection("notifications").add(notifData)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun loadLiveTracking() {
        setContentView(R.layout.fragment_live_tracking)
        isMapMaximized = false
        allMarkers.clear()
        
        findViewById<ImageButton>(R.id.btnBackTracking)?.setOnClickListener { loadHome() }
        
        val btnEnd = findViewById<TextView>(R.id.btnEndTrip)
        val mapView = findViewById<MapView>(R.id.mapView)
        val layoutBottom = findViewById<View>(R.id.layoutBottomInfo)
        val btnMaximize = findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnMyLocation = findViewById<ImageButton>(R.id.btnMyLocation)

        if (userRole == "Conductor") {
            mapView?.visibility = View.GONE
            btnMaximize?.visibility = View.GONE
            btnMyLocation?.visibility = View.GONE
            btnEnd?.text = "EXIT ROSTER"
            btnEnd?.setOnClickListener { loadHome() }
            
            val recycler = findViewById<RecyclerView>(R.id.recyclerPickup)
            recycler?.layoutParams?.height = LinearLayout.LayoutParams.MATCH_PARENT
        } else {
            mapView?.visibility = View.VISIBLE
            btnMaximize?.visibility = View.VISIBLE
            btnMyLocation?.visibility = View.VISIBLE
            btnEnd?.text = getString(CommonR.string.end_trip)
            btnEnd?.setOnClickListener { loadHome() }

            map = mapView
            map?.let { mv ->
                mv.setTileSource(TileSourceFactory.MAPNIK)
                mv.setMultiTouchControls(true)
                mv.controller.setZoom(15.0)
                mv.controller.setCenter(GeoPoint(14.5995, 120.9842))
                
                mv.setOnTouchListener { v, _ ->
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    false
                }

                val busIcon = getScaledDrawable(ContextCompat.getDrawable(this, CommonR.drawable.ic_bus), 48, 48)
                driverMarker = Marker(mv)
                driverMarker?.icon = busIcon
                driverMarker?.title = "Your Location"
                driverMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                driverMarker?.position = GeoPoint(14.5995, 120.9842)
                mv.overlays.add(driverMarker)

                if (stopIds.isNotEmpty()) {
                    var loaded = 0
                    val stopPoints = mutableMapOf<String, GeoPoint>()
                    
                    val stopIcon = getScaledDrawable(ContextCompat.getDrawable(this, CommonR.drawable.ic_stop_marker), 40, 40)
                    val nextStopIcon = getScaledDrawable(ContextCompat.getDrawable(this, CommonR.drawable.ic_stop_marker_red), 52, 52)

                    for ((index, sid) in stopIds.withIndex()) {
                        db.collection("stops").document(sid).get().addOnSuccessListener { sDoc ->
                            val lat = sDoc.getDouble("latitude")
                            val lng = sDoc.getDouble("longitude")
                            if (lat != null && lng != null) {
                                val p = GeoPoint(lat, lng)
                                stopPoints[sid] = p
                                val marker = Marker(mv)
                                marker.position = p
                                marker.title = sDoc.getString("name")
                                marker.icon = if (index == 0) nextStopIcon else stopIcon
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                mv.overlays.add(marker)
                                allMarkers[sid] = marker
                                
                                if (index == 0) {
                                    findViewById<TextView>(R.id.tvNextStopName)?.text = sDoc.getString("name") ?: "Next Stop"
                                    driverMarker?.position = p
                                    mv.controller.animateTo(p)
                                }
                            }
                            loaded++
                            if (loaded == stopIds.size) {
                                val ordered = stopIds.mapNotNull { stopPoints[it] }
                                if (ordered.size > 1) {
                                    val poly = Polyline(mv)
                                    poly.setPoints(ordered)
                                    poly.outlinePaint.color = Color.parseColor("#4A90E2")
                                    poly.outlinePaint.strokeWidth = 10f
                                    mv.overlays.add(poly)
                                }
                                mv.invalidate()
                            }
                        }
                    }
                }
                mv.invalidate()
            }

            btnMaximize?.setOnClickListener {
                isMapMaximized = !isMapMaximized
                layoutBottom?.isVisible = !isMapMaximized
                btnMaximize.setImageResource(if (isMapMaximized) CommonR.drawable.ic_close else CommonR.drawable.ic_eye)
            }

            btnMyLocation?.setOnClickListener {
                driverMarker?.let { dm ->
                    map?.controller?.animateTo(dm.position)
                    map?.controller?.setZoom(17.5)
                }
            }
        }

        findViewById<TextView>(R.id.tvLiveTrackingTitle)?.text = getString(CommonR.string.live_trip_format, assignedRouteName ?: "Route")
        findViewById<TextView>(R.id.tvBusIndicator)?.text = getString(CommonR.string.bus_format, assignedBusNumber ?: "N/A")
        findViewById<TextView>(R.id.tvRouteInfo)?.text = assignedRouteName ?: "ASSIGNED ROUTE"
        
        setupRealTimeStudentList(R.id.recyclerPickup)
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
                findViewById<TextView>(R.id.tvProfilePhone)?.text = doc.getString("phoneNumber") ?: doc.getString("phone") ?: "-"
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
