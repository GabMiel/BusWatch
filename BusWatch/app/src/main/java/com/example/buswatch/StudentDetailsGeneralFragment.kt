package com.example.buswatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class StudentDetailsGeneralFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    private var currentChildData: kotlin.collections.Map<String, Any>? = null
    private var isFromChildrenList: Boolean = false
    private var studentListener: ListenerRegistration? = null
    private lateinit var mapView: MapView

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
        Configuration.getInstance().userAgentValue = requireContext().packageName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_student_details_general, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = arguments?.getString("childName")

        mapView = view.findViewById(R.id.mapHomeLocation)
        mapView.setMultiTouchControls(true)

        view.findViewById<ImageButton>(R.id.btnGeneralEdit).setOnClickListener {
            showEditDialog()
        }

        fetchStudentData()
    }

    private fun fetchStudentData() {
        val uid = auth.currentUser?.uid ?: return

        studentListener?.remove()
        studentListener = db.collection("parents").document(uid)
            .addSnapshotListener { document, error ->
                if (error != null) return@addSnapshotListener
                if (isAdded && document != null && document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                    @Suppress("UNCHECKED_CAST")
                    val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                    
                    var foundChild: kotlin.collections.Map<String, Any>? = null
                    
                    if (childMap != null) {
                        val fullName = "${childMap["firstName"]} ${childMap["lastName"]}".trim()
                        if (childName == null || fullName == childName) {
                            foundChild = childMap
                            isFromChildrenList = false
                        }
                    }
                    
                    if (foundChild == null && childrenList != null) {
                        foundChild = childrenList.find { 
                            "${it["firstName"]} ${it["lastName"]}".trim() == childName 
                        }
                        if (foundChild != null) isFromChildrenList = true
                    }

                    currentChildData = foundChild
                    foundChild?.let { displayStudentInfo(it) }
                }
            }
    }

    private fun displayStudentInfo(child: kotlin.collections.Map<String, Any>) {
        val view = view ?: return
        
        val firstName = child["firstName"] as? String ?: ""
        val lastName = child["lastName"] as? String ?: ""
        val middleName = child["middleName"] as? String ?: ""
        val suffix = child["suffix"] as? String ?: ""
        
        val fullName = listOf(firstName, middleName, lastName, suffix)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            
        view.findViewById<TextView>(R.id.tvStudentName).text = fullName
        view.findViewById<TextView>(R.id.tvStudentId).text = (child["studentId"] as? String)?.takeIf { it.isNotEmpty() } ?: "---"
        view.findViewById<TextView>(R.id.tvDob).text = (child["age"] as? String)?.takeIf { it.isNotEmpty() } ?: "---"
        view.findViewById<TextView>(R.id.tvSchool).text = (child["school"] as? String)?.takeIf { it.isNotEmpty() } ?: "---"
        view.findViewById<TextView>(R.id.tvGrade).text = (child["grade"] as? String)?.takeIf { it.isNotEmpty() } ?: "---"
        
        // Fix: fetch section robustly
        val sectionStr = (child["class"] as? String)?.takeIf { it.isNotEmpty() } 
            ?: (child["section"] as? String)?.takeIf { it.isNotEmpty() } 
            ?: "---"
        view.findViewById<TextView>(R.id.tvSection).text = sectionStr
        
        view.findViewById<TextView>(R.id.tvAddress).text = (child["address"] as? String)?.takeIf { it.isNotEmpty() } ?: "---"

        // Fix: fetch child avatar URL robustly
        val avatarUrl = (child["childAvatarUrl"] as? String)?.takeIf { it.isNotEmpty() } 
            ?: (child["avatarUrl"] as? String)?.takeIf { it.isNotEmpty() }
            
        val avatarImg = view.findViewById<ImageView>(R.id.imgStudentAvatar)
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(CommonR.drawable.child)
                .error(CommonR.drawable.child)
                .circleCrop()
                .into(avatarImg)
        } else {
            avatarImg.setImageResource(CommonR.drawable.child)
        }

        val lat = (child["latitude"] as? Number)?.toDouble() ?: 0.0
        val lng = (child["longitude"] as? Number)?.toDouble() ?: 0.0
        
        if (lat != 0.0 && lng != 0.0) {
            val startPoint = GeoPoint(lat, lng)
            mapView.controller.setZoom(17.5)
            mapView.controller.setCenter(startPoint)
            
            mapView.overlays.clear()
            val marker = Marker(mapView)
            marker.position = startPoint
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = "Home Location"
            mapView.overlays.add(marker)
            mapView.invalidate()
        }
    }

    private fun showEditDialog() {
        val child = currentChildData ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_student_general, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        val etStudentId = dialogView.findViewById<EditText>(R.id.etEditStudentId)
        val etFirstName = dialogView.findViewById<EditText>(R.id.etEditFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etEditLastName)
        val etMiddleName = dialogView.findViewById<EditText>(R.id.etEditMiddleName)
        val tvSuffix = dialogView.findViewById<TextView>(R.id.tvEditStudentSelectedSuffix)
        val etAge = dialogView.findViewById<EditText>(R.id.etEditDob)
        val etSection = dialogView.findViewById<EditText>(R.id.etEditSection)
        val tvGrade = dialogView.findViewById<TextView>(R.id.tvEditSelectedGrade)
        val etSchool = dialogView.findViewById<EditText>(R.id.etEditSchool)
        val etAddress = dialogView.findViewById<EditText>(R.id.etEditAddress)
        val imgAvatar = dialogView.findViewById<ImageView>(R.id.imgEditStudentAvatar)

        // Pre-fill
        etStudentId.setText(child["studentId"] as? String ?: "")
        etFirstName.setText(child["firstName"] as? String ?: "")
        etLastName.setText(child["lastName"] as? String ?: "")
        etMiddleName.setText(child["middleName"] as? String ?: "")
        tvSuffix.text = (child["suffix"] as? String)?.takeIf { it.isNotEmpty() } ?: "Suffix"
        etAge.setText(child["age"] as? String ?: "")
        
        val section = (child["class"] as? String)?.takeIf { it.isNotEmpty() } ?: (child["section"] as? String) ?: ""
        etSection.setText(section)
        
        tvGrade.text = (child["grade"] as? String)?.takeIf { it.isNotEmpty() } ?: "Grade"
        etSchool.setText(child["school"] as? String ?: "")
        etAddress.setText(child["address"] as? String ?: "")

        val avatarUrl = (child["childAvatarUrl"] as? String)?.takeIf { it.isNotEmpty() } 
            ?: (child["avatarUrl"] as? String)?.takeIf { it.isNotEmpty() }
            
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(this).load(avatarUrl).placeholder(CommonR.drawable.child).circleCrop().into(imgAvatar)
        } else {
            imgAvatar.setImageResource(CommonR.drawable.child)
        }

        val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV")
        dialogView.findViewById<View>(R.id.btnEditStudentSuffix).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, which ->
                    tvSuffix.text = if (suffixes[which] == "None") "" else suffixes[which]
                }.show()
        }

        val grades = arrayOf("Nursery", "Kindergarten", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6", "Grade 7", "Grade 8", "Grade 9", "Grade 10", "Grade 11", "Grade 12")
        dialogView.findViewById<View>(R.id.btnEditGrade).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Grade")
                .setItems(grades) { _, which ->
                    tvGrade.text = grades[which]
                }.show()
        }

        dialogView.findViewById<ImageButton>(R.id.btnDismissEditGeneral).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<Button>(R.id.btnSaveStudent).setOnClickListener {
            val updatedChild = child.toMutableMap()
            updatedChild["studentId"] = etStudentId.text.toString().trim()
            updatedChild["firstName"] = etFirstName.text.toString().trim()
            updatedChild["lastName"] = etLastName.text.toString().trim()
            updatedChild["middleName"] = etMiddleName.text.toString().trim()
            updatedChild["suffix"] = if (tvSuffix.text == "Suffix") "" else tvSuffix.text.toString()
            updatedChild["age"] = etAge.text.toString().trim()
            
            // Save as both for safety
            updatedChild["class"] = etSection.text.toString().trim()
            updatedChild["section"] = etSection.text.toString().trim()

            updatedChild["grade"] = if (tvGrade.text == "Grade") "" else tvGrade.text.toString()
            updatedChild["school"] = etSchool.text.toString().trim()

            saveUpdatedData(updatedChild, dialog)
        }

        // Initialize dialog map if needed (view only for now)
        val dialogMapView = dialogView.findViewById<MapView>(R.id.mapEditHome)
        val lat = (child["latitude"] as? Number)?.toDouble() ?: 0.0
        val lng = (child["longitude"] as? Number)?.toDouble() ?: 0.0
        if (lat != 0.0 && lng != 0.0) {
            val point = GeoPoint(lat, lng)
            dialogMapView.controller.setZoom(17.5)
            dialogMapView.controller.setCenter(point)
            val marker = Marker(dialogMapView)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            dialogMapView.overlays.add(marker)
        }

        dialog.show()
    }

    private fun saveUpdatedData(updatedChild: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)
        
        if (isFromChildrenList) {
            docRef.get().addOnSuccessListener { document ->
                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>> ?: return@addOnSuccessListener
                val newList = childrenList.toMutableList()
                val index = newList.indexOfFirst { 
                    "${it["firstName"]} ${it["lastName"]}".trim() == childName 
                }
                
                if (index != -1) {
                    newList[index] = updatedChild
                    docRef.update("children", newList)
                        .addOnSuccessListener {
                            dialog.dismiss()
                            childName = "${updatedChild["firstName"]} ${updatedChild["lastName"]}".trim()
                            Toast.makeText(context, "Student information updated", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        } else {
            docRef.update("child", updatedChild)
                .addOnSuccessListener {
                    dialog.dismiss()
                    childName = "${updatedChild["firstName"]} ${updatedChild["lastName"]}".trim()
                    Toast.makeText(context, "Student information updated", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        studentListener?.remove()
    }
}
