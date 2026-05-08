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

    private val _currentTab = MutableLiveData("Morning")
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
        _currentTab.value = tab
        refreshStudentListener()
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
                            
                            _assignedRoute.value = RouteData(
                                routeId, routeName, busNumber, stopIds,
                                morningTimeStr, afternoonTimeStr, capacity, busId
                            )
                            refreshStudentListener()
                        }
                    } else {
                        _assignedRoute.value = RouteData(
                            routeId, routeName, null, stopIds,
                            morningTimeStr, afternoonTimeStr, 0, null
                        )
                        refreshStudentListener()
                    }
                }
            }
    }

    private fun fetchStopDetails(sIds: List<String>) {
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
                    // Update current students list with stop names if they were missing
                    val currentList = _students.value ?: emptyList()
                    val updated = currentList.map { student ->
                        if (student.stopName.isEmpty() || student.stopName == "Loading...") {
                            student.copy(stopName = stopNames[student.stopId] ?: "Unknown Stop")
                        } else student
                    }
                    _students.value = updated
                }
            }
        }
    }

    fun refreshStudentListener() {
        val route = _assignedRoute.value ?: return
        val stopIds = route.stopIds
        if (stopIds.isEmpty()) return

        studentListener?.remove()

        val queryStops = if (stopIds.size > 30) stopIds.take(30) else stopIds

        studentListener = db.collection("parents")
            .whereIn("child.stop", queryStops)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("DriverVM", "Firestore Error: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                
                val studentsList = mutableListOf<Student>()
                for (doc in snapshots) {
                    @Suppress("UNCHECKED_CAST")
                    val childMap = doc.get("child") as? Map<String, Any>
                    if (childMap != null) {
                        studentsList.add(mapMapToStudent(doc.id, childMap))
                    }
                    
                    @Suppress("UNCHECKED_CAST")
                    val additionalChildren = doc.get("children") as? List<Map<String, Any>>
                    additionalChildren?.forEachIndexed { index, map ->
                        val childStop = map["stop"] as? String ?: ""
                        if (childStop in queryStops) {
                            studentsList.add(mapMapToStudent("${doc.id}_$index", map))
                        }
                    }
                }
                _students.value = studentsList
                applySorting()
            }
    }

    private fun mapMapToStudent(id: String, map: Map<String, Any>): Student {
        val fName = map["firstName"] as? String ?: ""
        val lName = map["lastName"] as? String ?: ""
        val stopId = map["stop"] as? String ?: ""
        
        return Student(
            id, 
            "$fName $lName", 
            map["grade"] as? String ?: "N/A", 
            map["status"] as? String ?: "At Home", 
            map["childAvatarUrl"] as? String ?: "", 
            map["rideOption"] as? String ?: "Round Trip", 
            stopId,
            stopName = stopNames[stopId] ?: "Loading...",
            bloodType = map["bloodType"] as? String ?: "N/A",
            allergies = map["allergies"] as? String ?: "None",
            medications = map["medications"] as? String ?: "None",
            medicalConditions = map["medicalConditions"] as? String ?: "None",
            emergencyContact = map["emergencyContact"] as? String ?: "N/A",
            emergencyPhone = map["emergencyPhone"] as? String ?: "N/A"
        ).apply {
            _lastKnownLocation.value?.let { busLoc ->
                stopCoords[stopId]?.let { stopLoc ->
                    distanceMeters = busLoc.distanceToAsDouble(stopLoc).toInt()
                }
            }
        }
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

    fun updateStudentStatus(parentId: String, newStatus: String) {
        // Immediate local update for "instant" reflection
        val currentList = _students.value?.toMutableList() ?: mutableListOf()
        val index = currentList.indexOfFirst { it.id == parentId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(status = newStatus)
            _students.value = currentList
        }

        val actualId = parentId.split("_")[0]
        db.collection("parents").document(actualId).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val primary = doc.get("child") as? MutableMap<String, Any>
            if (primary != null && parentId == actualId) {
                primary["status"] = newStatus
                db.collection("parents").document(actualId).update("child", primary)
            } else {
                @Suppress("UNCHECKED_CAST")
                val children = doc.get("children") as? List<Map<String, Any>>
                val updatedChildren = children?.mapIndexed { index, map ->
                    if ("${actualId}_$index" == parentId) map.toMutableMap().apply { put("status", newStatus) }
                    else map
                }
                if (updatedChildren != null) db.collection("parents").document(actualId).update("children", updatedChildren)
            }
        }
    }

    fun sendStudentBoardingNotification(parentId: String, studentName: String) {
        val actualId = parentId.split("_")[0]
        val title = "Child Boarded"
        val message = "$studentName has successfully boarded the bus."
        val notifData = hashMapOf(
            "title" to title, "message" to message, "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false, "type" to "student_boarding"
        )
        db.collection("parents").document(actualId).collection("notifications").add(notifData)
        NotificationSender.sendNotification(actualId, title, message)
    }

    fun sendStudentArrivalNotification(parentId: String, studentName: String, status: String) {
        val actualId = parentId.split("_")[0]
        val destination = if (status == "At School") "school" else "home"
        val title = "Child Arrived"
        val message = "$studentName has safely arrived at $destination."
        val notifData = hashMapOf(
            "title" to title, "message" to message, "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false, "type" to "student_arrival"
        )
        db.collection("parents").document(actualId).collection("notifications").add(notifData)
        NotificationSender.sendNotification(actualId, title, message)
    }

    fun sendTripStartNotification() {
        val route = _assignedRoute.value
        if (route == null) {
            _toastMessage.value = "Error: No active route found for your account."
            return
        }

        val stopIds = route.stopIds
        if (stopIds.isEmpty()) return
        
        // Notifications are sent in background, immediate feedback is handled in the UI click listener
        val chunks = stopIds.chunked(30)
        for (chunk in chunks) {
            db.collection("parents")
                .whereIn("child.stop", chunk)
                .get()
                .addOnSuccessListener { snapshots ->
                    if (snapshots.isEmpty) return@addOnSuccessListener

                    val parentIds = snapshots.map { it.id }
                    val title = "Bus Trip Started"
                    val message = "The bus for ${route.name} has started its trip. Be ready at your stop!"

                    parentIds.forEach { pid ->
                        val notifData = hashMapOf(
                            "title" to title, "message" to message, "timestamp" to FieldValue.serverTimestamp(),
                            "isRead" to false, "type" to "trip_start"
                        )
                        db.collection("parents").document(pid).collection("notifications").add(notifData)
                    }
                    NotificationSender.sendNotification(parentIds, title, message)
                }
                .addOnFailureListener { e ->
                    Log.e("DriverVM", "Failed to notify parents: ${e.message}")
                }
        }
    }

    fun sendSOSNotification() {
        val route = _assignedRoute.value ?: return
        val chunks = route.stopIds.chunked(30)
        for (chunk in chunks) {
            db.collection("parents")
                .whereIn("child.stop", chunk)
                .get()
                .addOnSuccessListener { snapshots ->
                    if (snapshots.isEmpty) return@addOnSuccessListener
                    val parentIds = snapshots.map { it.id }
                    val title = "⚠️ EMERGENCY: SOS Alert"
                    val message = "An SOS alert has been triggered for Bus ${route.busNumber ?: route.name}. Open the app for live location."

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
    }

    override fun onCleared() {
        super.onCleared()
        studentListener?.remove()
    }
}
