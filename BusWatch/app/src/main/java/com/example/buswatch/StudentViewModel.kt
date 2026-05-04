package com.example.buswatch

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _studentData = MutableLiveData<kotlin.collections.Map<String, Any>?>()
    val studentData: LiveData<kotlin.collections.Map<String, Any>?> = _studentData

    private val _parentStatus = MutableLiveData<String>()
    val parentStatus: LiveData<String> = _parentStatus

    private val _activeStops = MutableLiveData<List<kotlin.collections.Map<String, Any>>>()
    val activeStops: LiveData<List<kotlin.collections.Map<String, Any>>> = _activeStops

    private val _updateResult = MutableLiveData<Result<String>>()
    val updateResult: LiveData<Result<String>> = _updateResult

    fun loadStudentAndParentData(childName: String?) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                _parentStatus.value = doc.getString("status") ?: "pending"
                
                @Suppress("UNCHECKED_CAST")
                val childMap = doc.get("child") as? kotlin.collections.Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<kotlin.collections.Map<String, Any>>
                
                var foundChild: kotlin.collections.Map<String, Any>? = null
                
                if (childMap != null) {
                    val firstName = childMap["firstName"] as? String ?: ""
                    val lastName = childMap["lastName"] as? String ?: ""
                    val fullName = "$firstName $lastName".trim()
                    if (childName == null || fullName == childName) {
                        foundChild = childMap
                    }
                }
                
                if (foundChild == null && childrenList != null) {
                    foundChild = childrenList.find { 
                        val firstName = it["firstName"] as? String ?: ""
                        val lastName = it["lastName"] as? String ?: ""
                        "$firstName $lastName".trim() == childName 
                    }
                }
                _studentData.value = foundChild
            }
        }
    }

    fun loadActiveStops() {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val stops = snapshots.map { doc ->
                doc.data.toMutableMap().apply { put("id", doc.id) }
            }
            _activeStops.value = stops
        }
    }

    fun updateStudentGeneral(uid: String, currentChildName: String?, updatedData: MutableMap<String, Any>, avatarUri: Uri?) {
        if (avatarUri != null) {
            uploadChildAvatar(uid, avatarUri) { avatarUrl ->
                updatedData["childAvatarUrl"] = avatarUrl
                saveStudentData(uid, currentChildName, updatedData)
            }
        } else {
            saveStudentData(uid, currentChildName, updatedData)
        }
    }

    private fun uploadChildAvatar(uid: String, uri: Uri, onComplete: (String) -> Unit) {
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
                    val url = resultData?.get("secure_url") as? String ?: ""
                    onComplete(url)
                }
                override fun onError(requestId: String, error: ErrorInfo?) {
                    _updateResult.value = Result.failure(Exception("Upload failed"))
                }
                override fun onReschedule(requestId: String, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveStudentData(uid: String, currentChildName: String?, updatedFields: kotlin.collections.Map<String, Any>) {
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val childMap = doc.get("child") as? kotlin.collections.Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val childrenList = doc.get("children") as? List<kotlin.collections.Map<String, Any>>
            
            if (childMap != null) {
                val firstName = childMap["firstName"] as? String ?: ""
                val lastName = childMap["lastName"] as? String ?: ""
                val fullName = "$firstName $lastName".trim()
                if (currentChildName == null || fullName == currentChildName) {
                    val finalChild = childMap.toMutableMap()
                    finalChild.putAll(updatedFields)
                    db.collection("parents").document(uid).update("child", finalChild).addOnSuccessListener {
                        _updateResult.value = Result.success("Updated successfully")
                        val newName = "${finalChild["firstName"]} ${finalChild["lastName"]}".trim()
                        loadStudentAndParentData(newName)
                    }
                    return@addOnSuccessListener
                }
            }
            
            if (childrenList != null) {
                val newList = childrenList.toMutableList()
                val index = newList.indexOfFirst { 
                    val firstName = it["firstName"] as? String ?: ""
                    val lastName = it["lastName"] as? String ?: ""
                    "$firstName $lastName".trim() == currentChildName 
                }
                if (index != -1) {
                    val finalChild = newList[index].toMutableMap()
                    finalChild.putAll(updatedFields)
                    newList[index] = finalChild
                    db.collection("parents").document(uid).update("children", newList).addOnSuccessListener {
                        _updateResult.value = Result.success("Updated successfully")
                        val newName = "${finalChild["firstName"]} ${finalChild["lastName"]}".trim()
                        loadStudentAndParentData(newName)
                    }
                }
            }
        }.addOnFailureListener {
            _updateResult.value = Result.failure(it)
        }
    }
}
