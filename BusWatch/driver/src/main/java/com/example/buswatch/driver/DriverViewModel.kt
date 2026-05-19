package com.example.buswatch.driver

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.buswatch.common.NotificationSender
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.util.GeoPoint
import java.util.Calendar

data class RouteData(
    val id: String,
    val name: String,
    val busNumber: String?,
    val stopIds: List<String>,
    val morningTime: String,
    val afternoonTime: String,
    val busCapacity: Int,
    val busId: String? = null
)

class DriverViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _userRole = MutableLiveData("Driver")
    val userRole: LiveData<String> = _userRole

    private val _userName = MutableLiveData("")
    val userName: LiveData<String> = _userName

    private val _assignedRoute = MutableLiveData<RouteData?>()
    val assignedRoute: LiveData<RouteData?> = _assignedRoute

    private val _students = MutableLiveData<List<Student>>(emptyList())
    val students: LiveData<List<Student>> = _students

    private val _currentTab = MutableLiveData<String>().apply {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        value = if (hour < 12) "Morning" else "Afternoon"
    }
    val currentTab: LiveData<String> = _currentTab

    private val _lastKnownLocation = MutableLiveData<GeoPoint?>()
    val lastKnownLocation: LiveData<GeoPoint?> = _lastKnownLocation

    private val _lastBearing = MutableLiveData(0f)
    val lastBearing: LiveData<Float> = _lastBearing

    private val _sortMode = MutableLiveData("Name") 
    private val _isSortAscending = MutableLiveData(true)

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private var studentListener: ListenerRegistration? = null
    val stopCoords = mutableMapOf<String, GeoPoint>()
    val stopNames = mutableMapOf<String, String>()

    fun setCurrentTab(tab: String) {
        if (_currentTab.value != tab) {
            _currentTab.value = tab
            applySorting()
        }
    }

    fun setLocation(location: GeoPoint, bearing: Float = 0f) {
        _lastKnownLocation.value = location
        if (bearing != 0f) {
            _lastBearing.value = bearing
        }
        updateStudentDistances(location)
    }

    fun setSortMode(mode: String, ascending: Boolean) {
        _sortMode.value = mode
        _isSortAscending.value = ascending
        applySorting()
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun fetchUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("drivers").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                _userRole.value = "Driver"
                _userName.value = doc.getString("firstName") ?: "Driver"
                fetchAssignedRoute(uid, "Driver")
            } else {
                db.collection("conductors").document(uid).get().addOnSuccessListener { cDoc ->
                    if (cDoc.exists()) {
                        _userRole.value = "Conductor"
                        _userName.value = cDoc.getString("firstName") ?: "Conductor"
                        fetchAssignedRoute(uid, "Conductor")
                    }
                }
            }
        }
    }

    private fun fetchAssignedRoute(uid: String, role: String) {
        val field = if (role == "Driver") "driverId" else "conductorId"
        db.collection("routes")
            .whereEqualTo(field, uid)
            .whereEqualTo("status", "Active")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                if (!snapshots.isEmpty) {
                    val doc = snapshots.documents[0]
                    val routeId = doc.id
                    val routeName = doc.getString("routeName") ?: ""
                    val ms = doc.getString("morningStartTime") ?: ""
                    val me = doc.getString("morningEndTime") ?: ""
                    val asTime = doc.getString("afternoonStartTime") ?: ""
                    val ae = doc.getString("afternoonEndTime") ?: ""
                    
                    val morningTimeStr = if (ms.isNotEmpty() && me.isNotEmpty()) "$ms - $me" else "No Schedule"
                    val afternoonTimeStr = if (asTime.isNotEmpty() && ae.isNotEmpty()) "$asTime - $ae" else "No Schedule"
                    
                    @Suppress("UNCHECKED_CAST")
                    val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
                    fetchStopDetails(stopIds)
                    
                    val busId = doc.getString("busId") ?: ""
                    if (busId.isNotEmpty()) {
                        db.collection("buses").document(busId).get().addOnSuccessListener { busDoc ->
                            val busNumber = busDoc.getString("busNumber")
                            val capacity = (busDoc.get("capacity") as? Number)?.toInt() ?: 0
                            _assignedRoute.value = RouteData(routeId, routeName, busNumber, stopIds, morningTimeStr, afternoonTimeStr, capacity, busId)
                            refreshStudentListener()
                        }
                    } else {
                        _assignedRoute.value = RouteData(routeId, routeName, null, stopIds, morningTimeStr, afternoonTimeStr, 0, null)
                        refreshStudentListener()
                    }
                }
            }
    }

    private fun fetchStopDetails(sIds: List<String>) {
        if (sIds.isEmpty()) return
        var loaded = 0
        for (sid in sIds) {
            db.collection("stops").document(sid).get().addOnSuccessListener { doc ->
                val lat = doc.getDouble("latitude")
                val lng = doc.getDouble("longitude")
                val name = doc.getString("name") ?: "Unknown Stop"
                if (lat != null && lng != null) {
                    stopCoords[sid] = GeoPoint(lat, lng)
                    stopNames[sid] = name
                }
                loaded++
                if (loaded == sIds.size) {
                    val currentList = _students.value ?: emptyList()
                    val updated = currentList.map { student ->
                        if (student.stopName.isEmpty() || student.stopName == "Loading...") {
                            student.copy(stopName = stopNames[student.stopId] ?: "Unknown Stop")
                        } else student
                    }
                    _students.value = updated
                    applySorting()
                }
            }
        }
    }

    fun refreshStudentListener() {
        val route = _assignedRoute.value ?: return
        val stopIds = route.stopIds
        if (stopIds.isEmpty()) return

        studentListener?.remove()

        studentListener = db.collection("parents")
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("DriverVM", "Firestore Error: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                
                val studentsList = mutableListOf<Student>()
                for (doc in snapshots) {
                    val data = doc.data
                    
                    @Suppress("UNCHECKED_CAST")
                    val childMap = data["child"] as? Map<String, Any>
                    if (childMap != null) {
                        val stopId = childMap["stop"] as? String ?: ""
                        if (stopId in stopIds) {
                            studentsList.add(mapMapToStudent(doc.id, childMap, data))
                        }
                    }
                    
                    @Suppress("UNCHECKED_CAST")
                    val additionalChildren = data["children"] as? List<Map<String, Any>>
                    additionalChildren?.forEachIndexed { index, map ->
                        val stopId = map["stop"] as? String ?: ""
                        if (stopId in stopIds) {
                            studentsList.add(mapMapToStudent("${doc.id}_$index", map, data))
                        }
                    }
                }
                _students.value = studentsList
                applySorting()
            }
    }

    private fun mapMapToStudent(id: String, map: Map<String, Any>, parentData: Map<String, Any>): Student {
        val fName = map["firstName"] as? String ?: ""
        val lName = map["lastName"] as? String ?: ""
        val stopId = map["stop"] as? String ?: ""
        
        val rideOption = (map["rideOption"] as? String)?.takeIf { it.isNotBlank() } 
            ?: "Round Trip (Morning & Afternoon)"

        @Suppress("UNCHECKED_CAST")
        val profile = parentData["profile"] as? Map<String, Any>
        val pFirstName = profile?.get("firstName") as? String ?: parentData["firstName"] as? String ?: ""
        val pLastName = profile?.get("lastName") as? String ?: parentData["lastName"] as? String ?: ""
        val pPhone = profile?.get("phone") as? String ?: parentData["phone"] as? String ?: "N/A"
        val parentFullName = if (pFirstName.isNotEmpty() || pLastName.isNotEmpty()) "$pFirstName $pLastName".trim() else "Parent"
        
        val student = Student(
            id, 
            "$fName $lName", 
            map["grade"] as? String ?: "N/A", 
            map["status"] as? String ?: "At Home", 
            map["childAvatarUrl"] as? String ?: "", 
            rideOption, 
            stopId,
            stopName = stopNames[stopId] ?: "Loading...",
            bloodType = map["bloodType"] as? String ?: "N/A",
            allergies = map["allergies"] as? String ?: "None",
            medications = map["medications"] as? String ?: "None",
            medicalConditions = map["medicalConditions"] as? String ?: "None",
            emergencyContact = parentFullName,
            emergencyPhone = pPhone
        )
        
        _lastKnownLocation.value?.let { busLoc ->
            stopCoords[stopId]?.let { stopLoc ->
                student.distanceMeters = busLoc.distanceToAsDouble(stopLoc).toInt()
            }
        }
        return student
    }

    private fun applySorting() {
        val currentList = _students.value?.toMutableList() ?: return
        val mode = _sortMode.value
        val ascending = _isSortAscending.value ?: true
        val route = _assignedRoute.value
        val tab = _currentTab.value

        if (mode == "Stop" && route != null) {
            val displayStopIds = if (tab == "Afternoon") route.stopIds.reversed() else route.stopIds
            currentList.sortWith(compareBy { student ->
                val index = displayStopIds.indexOf(student.stopId)
                if (index == -1) Int.MAX_VALUE else index
            })
        } else {
            currentList.sortBy { it.name }
        }
        
        if (!ascending) currentList.reverse()
        _students.value = currentList
    }

    private fun updateStudentDistances(busLoc: GeoPoint) {
        val currentList = _students.value ?: return
        var changed = false
        for (student in currentList) {
            stopCoords[student.stopId]?.let { stopLoc ->
                val dist = busLoc.distanceToAsDouble(stopLoc).toInt()
                if (student.distanceMeters != dist) {
                    student.distanceMeters = dist
                    changed = true
                }
            }
        }
        if (changed) _students.value = currentList
    }

    /**
     * Resets student statuses based on the session to ensure they appear Green when trip starts,
     * but only for those who need to be picked up.
     */
    fun startTrip() {
        val tab = _currentTab.value ?: "Morning"
        val studentsList = _students.value ?: return
        
        studentsList.forEach { student ->
            if (isStudentEligible(student, tab)) {
                val status = student.status.lowercase().trim()
                if (tab == "Morning") {
                    // Morning: If they were At School from yesterday, reset to At Home
                    if (status == "at school") {
                        updateStudentStatus(student.id, "At Home")
                    }
                } else {
                    // Afternoon: If they were At Home from morning/yesterday, move to At School
                    if (status == "at home") {
                        updateStudentStatus(student.id, "At School")
                    }
                }
            }
        }
        sendTripStartNotification()
    }

    fun resetAllStatuses() {
        val currentList = _students.value ?: return
        currentList.forEach { student ->
            updateStudentStatus(student.id, "At Home")
        }
        _toastMessage.value = "All students reset to At Home"
    }

    private fun isStudentEligible(student: Student, tab: String): Boolean {
        val ride = student.rideOption.lowercase()
        return when (tab) {
            "Morning" -> ride.contains("morning") || ride.contains("round trip")
            "Afternoon" -> ride.contains("afternoon") || ride.contains("round trip")
            else -> !ride.contains("not riding")
        }
    }

    fun updateStudentStatus(studentId: String, newStatus: String) {
        val actualId = studentId.split("_")[0]
        db.collection("parents").document(actualId).get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener
            
            val updates = mutableMapOf<String, Any>()
            if (studentId == actualId) {
                val childData = doc.get("child") as? Map<*, *>
                if (childData != null) {
                    val updatedChild = childData.toMutableMap()
                    updatedChild["status"] = newStatus
                    updates["child"] = updatedChild
                }
            } else {
                val children = doc.get("children") as? List<Map<*, *>>
                if (children != null) {
                    val updatedChildren = children.mapIndexed { index, map ->
                        if ("${actualId}_$index" == studentId) {
                            map.toMutableMap().apply { put("status", newStatus) }
                        } else map
                    }
                    updates["children"] = updatedChildren
                }
            }
            
            if (updates.isNotEmpty()) {
                db.collection("parents").document(actualId).update(updates)
            }
        }
    }

    /**
     * Handles dropping off a student. 
     * Morning -> At School (Turns Orange in morning, Green in afternoon).
     * Afternoon -> At Home (Turns Orange in afternoon, Green next morning).
     */
    fun dropOffStudent(student: Student) {
        val tab = _currentTab.value ?: "Morning"
        val finalStatus = if (tab == "Morning") "At School" else "At Home"
        
        updateStudentStatus(student.id, finalStatus)
        sendStudentArrivalNotification(student.id, student.name, finalStatus)
    }

    fun dropAllStudents() {
        val currentList = _students.value ?: return
        val tab = _currentTab.value ?: "Morning"
        val finalStatus = if (tab == "Morning") "At School" else "At Home"
        
        currentList.forEach { student ->
            val status = student.status.lowercase().trim()
            if (status == "on board" || status == "riding") {
                updateStudentStatus(student.id, finalStatus)
                sendStudentArrivalNotification(student.id, student.name, finalStatus)
            }
        }
        _toastMessage.value = "All students on board have been dropped off"
    }

    private fun getFormattedBusName(busName: String?, defaultIfNull: String): String {
        if (busName.isNullOrBlank()) return defaultIfNull
        return if (busName.trim().startsWith("bus", ignoreCase = true)) {
            busName.trim()
        } else {
            "Bus $busName"
        }
    }

    fun sendStudentBoardingNotification(parentId: String, studentName: String) {
        val route = _assignedRoute.value
        val actualId = parentId.split("_")[0]
        val title = "Child Boarded"
        val busInfo = getFormattedBusName(route?.busNumber, "the bus").let { 
            if (it == "the bus") it else it.lowercase() 
        }
        // Ensuring it looks natural: "Child has boarded Bus 1" or "Child has boarded the bus"
        val message = "$studentName has successfully boarded ${getFormattedBusName(route?.busNumber, "the bus")}."
        val notifData = hashMapOf(
            "title" to title, "message" to message, "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false, "type" to "student_boarding"
        )
        db.collection("parents").document(actualId).collection("notifications").add(notifData)
        NotificationSender.sendNotification(actualId, title, message)
    }

    fun sendStudentArrivalNotification(parentId: String, studentName: String, status: String) {
        val route = _assignedRoute.value
        val actualId = parentId.split("_")[0]
        val destination = if (status == "At School") "school" else "home"
        val title = "Child Arrived"
        val busInfo = getFormattedBusName(route?.busNumber, "the bus")
        val message = "$studentName has safely arrived at $destination via $busInfo."
        val notifData = hashMapOf(
            "title" to title, "message" to message, "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false, "type" to "student_arrival"
        )
        db.collection("parents").document(actualId).collection("notifications").add(notifData)
        NotificationSender.sendNotification(actualId, title, message)
    }

    fun sendTripStartNotification() {
        val route = _assignedRoute.value ?: return
        db.collection("parents")
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { snapshots ->
                val parentIdsToNotify = mutableSetOf<String>()
                for (doc in snapshots) {
                    val data = doc.data
                    val stopId = (data["child"] as? Map<*, *>)?.get("stop") as? String
                    if (stopId in route.stopIds) {
                        parentIdsToNotify.add(doc.id)
                    } else {
                        val children = data["children"] as? List<Map<*, *>>
                        if (children?.any { it["stop"] in route.stopIds } == true) {
                            parentIdsToNotify.add(doc.id)
                        }
                    }
                }

                val title = "Bus Trip Started"
                val busInfo = getFormattedBusName(route.busNumber, "The bus")
                val message = "$busInfo for ${route.name} has started its trip. Be ready at your stop!"
                parentIdsToNotify.forEach { pid ->
                    val notifData = hashMapOf(
                        "title" to title, "message" to message, "timestamp" to FieldValue.serverTimestamp(),
                        "isRead" to false, "type" to "trip_start"
                    )
                    db.collection("parents").document(pid).collection("notifications").add(notifData)
                    NotificationSender.sendNotification(pid, title, message)
                }
            }
    }

    fun sendSOSNotification() {
        val route = _assignedRoute.value ?: return
        db.collection("parents")
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { snapshots ->
                val parentIds = snapshots.filter { doc ->
                    val data = doc.data
                    val stopId = (data["child"] as? Map<*, *>)?.get("stop") as? String
                    val children = data["children"] as? List<Map<*, *>>
                    stopId in route.stopIds || children?.any { it["stop"] in route.stopIds } == true
                }.map { it.id }

                val title = "⚠️ EMERGENCY: SOS Alert"
                val busInfo = getFormattedBusName(route.busNumber ?: route.name, "the bus")
                val message = "An SOS alert has been triggered for $busInfo. Open the app for live location."
                parentIds.forEach { pid ->
                    val notifData = hashMapOf(
                        "title" to title, "message" to message, "timestamp" to FieldValue.serverTimestamp(),
                        "isRead" to false, "type" to "sos_alert"
                    )
                    db.collection("parents").document(pid).collection("notifications").add(notifData)
                }
                NotificationSender.sendNotification(parentIds, title, message)
            }
    }

    override fun onCleared() {
        super.onCleared()
        studentListener?.remove()
    }
}
