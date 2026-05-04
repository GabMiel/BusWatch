package com.example.buswatch

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
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
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.views.MapView

class EditStudentDialogFragment : DialogFragment() {

    private lateinit var viewModel: StudentViewModel
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var childName: String? = null
    private var parentStatus: String = "pending"
    private var tempAvatarUri: Uri? = null
    private var dialogAvatarView: ImageView? = null
    
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            tempAvatarUri = it
            dialogAvatarView?.let { iv ->
                Glide.with(this).load(it).circleCrop().into(iv)
            }
        }
    }

    companion object {
        fun newInstance(childName: String?, parentStatus: String): EditStudentDialogFragment {
            val fragment = EditStudentDialogFragment()
            val args = Bundle()
            args.putString("childName", childName)
            args.putString("parentStatus", parentStatus)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, CommonR.style.CustomDialog)
        viewModel = ViewModelProvider(requireActivity()).get(StudentViewModel::class.java)
        childName = arguments?.getString("childName")
        parentStatus = arguments?.getString("parentStatus") ?: "pending"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_edit_student_general, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uid = auth.currentUser?.uid ?: return
        dialogAvatarView = view.findViewById(R.id.imgEditStudentAvatar)

        val etStudentId = view.findViewById<EditText>(R.id.etEditStudentId)
        val etFirstName = view.findViewById<EditText>(R.id.etEditFirstName)
        val etLastName = view.findViewById<EditText>(R.id.etEditLastName)
        val etMiddleName = view.findViewById<EditText>(R.id.etEditMiddleName)
        val etAge = view.findViewById<EditText>(R.id.etEditDob)
        val etSection = view.findViewById<EditText>(R.id.etEditSection)
        val etSchool = view.findViewById<EditText>(R.id.etEditSchool)
        val etAddress = view.findViewById<EditText>(R.id.etEditAddress)
        
        val tvSelectedSuffix = view.findViewById<TextView>(R.id.tvEditStudentSelectedSuffix)
        var selectedSuffix = ""
        val tvSelectedGrade = view.findViewById<TextView>(R.id.tvEditSelectedGrade)
        var selectedGrade = ""

        var currentLat = 0.0
        var currentLng = 0.0
        
        val btnChangeStop = view.findViewById<Button>(R.id.btnEditChangeStop)

        viewModel.studentData.value?.let { child ->
            etStudentId.setText(child["studentId"] as? String ?: "")
            etFirstName.setText(child["firstName"] as? String ?: "")
            etLastName.setText(child["lastName"] as? String ?: "")
            etMiddleName.setText(child["middleName"] as? String ?: "")
            etAge.setText(child["age"]?.toString() ?: "")
            etSection.setText(child["class"] as? String ?: child["section"] as? String ?: "")
            etSchool.setText(child["school"] as? String ?: "")
            etAddress.setText(child["address"] as? String ?: "")
            
            selectedSuffix = child["suffix"] as? String ?: ""
            tvSelectedSuffix.text = selectedSuffix.ifEmpty { getString(CommonR.string.suffix) }
            
            selectedGrade = child["grade"] as? String ?: ""
            tvSelectedGrade.text = selectedGrade.ifEmpty { getString(CommonR.string.grade) }
            
            val avatar = child["childAvatarUrl"] as? String ?: child["avatarUrl"] as? String ?: ""
            if (avatar.isNotEmpty()) Glide.with(this).load(avatar).circleCrop().into(dialogAvatarView!!)
            
            val stopId = child["stop"] as? String ?: ""
            if (stopId.isNotEmpty()) {
                db.collection("stops").document(stopId).get().addOnSuccessListener { sDoc ->
                    if (isAdded) view.findViewById<TextView>(R.id.tvEditAssignedStop).text = sDoc.getString("name") ?: stopId
                }
                btnChangeStop.setText(CommonR.string.request_change_stop_caps)
            } else {
                btnChangeStop.setText(CommonR.string.assign_stop_caps)
            }
            
            currentLat = child["latitude"] as? Double ?: 0.0
            currentLng = child["longitude"] as? Double ?: 0.0
        }

        view.findViewById<Button>(R.id.btnRequestAddressEdit).setOnClickListener {
            ConfirmPickupLocationDialogFragment.newInstance(
                childName,
                etAddress.text.toString(),
                currentLat,
                currentLng
            ).show(parentFragmentManager, "confirm_pickup_location")
            dismiss()
        }

        view.findViewById<FrameLayout>(R.id.btnEditStudentSuffix).setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(requireContext()).setItems(suffixes) { _, pos ->
                selectedSuffix = if (pos == 0) "" else suffixes[pos]
                tvSelectedSuffix.text = selectedSuffix.ifEmpty { getString(CommonR.string.suffix) }
            }.show()
        }

        view.findViewById<FrameLayout>(R.id.btnEditGrade).setOnClickListener {
            val grades = resources.getStringArray(CommonR.array.grades_array)
            AlertDialog.Builder(requireContext()).setItems(grades) { _, pos -> 
                selectedGrade = grades[pos]
                tvSelectedGrade.text = selectedGrade 
            }.show()
        }

        btnChangeStop.setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                val childData = viewModel.studentData.value
                val homeLat = childData?.get("latitude") as? Double ?: 0.0
                val homeLng = childData?.get("longitude") as? Double ?: 0.0
                StopPickerDialogFragment.newInstance(homeLat, homeLng, childName).show(parentFragmentManager, "stop_picker")
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Restricted until approved", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.rlEditStudentAvatar).setOnClickListener { pickAvatarLauncher.launch("image/*") }
        view.findViewById<ImageButton>(R.id.btnDismissEditGeneral).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener { dismiss() }
        
        val btnSave = view.findViewById<Button>(R.id.btnSaveStudent)
        btnSave.setOnClickListener {
            btnSave.isEnabled = false
            val updatedData = mutableMapOf<String, Any>(
                "studentId" to etStudentId.text.toString().trim(),
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
            viewModel.updateStudentGeneral(uid, childName, updatedData, tempAvatarUri)
            dismiss()
        }
    }
}
