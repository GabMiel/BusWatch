package com.example.buswatch.admin.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore

class ParentEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var user: UserAdmin
    
    private var parentAvatarUri: Uri? = null
    private val childrenUris = mutableMapOf<String, Uri>() // tag (child or children_index) to Uri
    private val originalChildrenData = mutableMapOf<String, Map<String, Any>>()
    
    private lateinit var pickParentImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickChildImageLauncher: ActivityResultLauncher<Intent>
    private var currentChildTagForPhoto: String? = null

    companion object {
        fun newInstance(user: UserAdmin) = ParentEditFragment().apply {
            this.user = user
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickParentImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { uri ->
                    parentAvatarUri = uri
                    view?.findViewById<ImageView>(R.id.imgParent)?.let {
                        Glide.with(this).load(uri).circleCrop().into(it)
                    }
                }
            }
        }
        pickChildImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { uri ->
                    currentChildTagForPhoto?.let { tag ->
                        childrenUris[tag] = uri
                        val container = view?.findViewById<LinearLayout>(R.id.layoutChildrenContainer)
                        for (i in 0 until (container?.childCount ?: 0)) {
                            val itemView = container?.getChildAt(i)
                            if (itemView?.tag == tag) {
                                Glide.with(this).load(uri).circleCrop().into(itemView.findViewById(R.id.imgChild))
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_parent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadData(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackEditParent)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.replaceFragment(UsersFragment())
        }
        
        view.findViewById<View>(R.id.btnSaveParentChanges)?.setOnClickListener { 
            saveAllChanges(view)
        }
        
        view.findViewById<View>(R.id.btnCancelParentChanges)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.replaceFragment(UsersFragment())
        }

        view.findViewById<View>(R.id.btnChangeParentPhoto)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickParentImageLauncher.launch(intent)
        }

        view.findViewById<View>(R.id.btnEditParentSuffix)?.setOnClickListener {
            showSuffixDialog(view.findViewById(R.id.tvSuffix))
        }
    }

    private fun showSuffixDialog(textView: TextView) {
        val options = arrayOf("NONE", "JR.", "SR.", "II", "III", "IV")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Suffix")
            .setItems(options) { _, which -> textView.text = options[which] }
            .show()
    }

    private fun showGradeDialog(textView: TextView) {
        val options = arrayOf("Nursery", "Kinder", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6", "Grade 7", "Grade 8", "Grade 9", "Grade 10", "Grade 11", "Grade 12")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Grade")
            .setItems(options) { _, which -> textView.text = options[which] }
            .show()
    }

    private fun showBloodTypeDialog(textView: TextView) {
        val options = arrayOf("-", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Blood Type")
            .setItems(options) { _, which -> textView.text = options[which] }
            .show()
    }

    private fun loadData(view: View) {
        val childrenContainer = view.findViewById<LinearLayout>(R.id.layoutChildrenContainer)
        db.collection("parents").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            @Suppress("UNCHECKED_CAST")
            val profile = doc.get("profile") as? Map<String, Any>
            
            view.findViewById<EditText>(R.id.etFirstName).setText(profile?.get("firstName") as? String ?: doc.getString("firstName") ?: "")
            view.findViewById<EditText>(R.id.etLastName).setText(profile?.get("lastName") as? String ?: doc.getString("lastName") ?: "")
            view.findViewById<EditText>(R.id.etMiddleName).setText(profile?.get("middleName") as? String ?: "")
            view.findViewById<TextView>(R.id.tvSuffix).text = profile?.get("suffix") as? String ?: "NONE"
            view.findViewById<EditText>(R.id.etPhone).setText(profile?.get("phone") as? String ?: doc.getString("phone") ?: "")
            
            @Suppress("UNCHECKED_CAST")
            val emergencyContacts = doc.get("emergencyContacts") as? List<Map<String, Any>>
            if (emergencyContacts != null && emergencyContacts.isNotEmpty()) {
                val first = emergencyContacts[0]
                view.findViewById<EditText>(R.id.etEmergencyName).setText(first["name"] as? String ?: "")
                view.findViewById<EditText>(R.id.etEmergencyPhone).setText(first["phone"] as? String ?: "")
                view.findViewById<EditText>(R.id.etRelationship).setText(first["relationship"] as? String ?: "")
            }

            view.findViewById<ImageView>(R.id.imgParent)?.let { img ->
                val avatar = parentAvatarUri ?: profile?.get("parentAvatarUrl") as? String ?: doc.getString("avatarUrl") ?: ""
                Glide.with(this).load(avatar)
                    .placeholder(CommonR.drawable.ic_person_placeholder)
                    .circleCrop()
                    .into(img)
            }

            childrenContainer?.removeAllViews()
            originalChildrenData.clear()

            // Fetch children from document fields
            @Suppress("UNCHECKED_CAST")
            val primaryChild = doc.get("child") as? Map<String, Any>
            if (primaryChild != null) {
                originalChildrenData["child"] = primaryChild
                addChildEditor(primaryChild, childrenContainer, "child")
            }

            @Suppress("UNCHECKED_CAST")
            val additionalChildren = doc.get("children") as? List<Map<String, Any>>
            additionalChildren?.forEachIndexed { index, childData ->
                val tag = "children_$index"
                originalChildrenData[tag] = childData
                addChildEditor(childData, childrenContainer, tag)
            }

            // Fallback: Check sub-collection if no children found in document
            if (primaryChild == null && (additionalChildren == null || additionalChildren.isEmpty())) {
                doc.reference.collection("students").get().addOnSuccessListener { studentDocs ->
                    for (studentDoc in studentDocs) {
                        originalChildrenData[studentDoc.id] = studentDoc.data
                        addChildEditor(studentDoc.data, childrenContainer, studentDoc.id)
                    }
                }
            }
        }
    }

    private fun addChildEditor(childData: Map<String, Any>, container: LinearLayout?, tag: String) {
        val itemView = layoutInflater.inflate(R.layout.layout_edit_child_item, container, false)
        itemView.tag = tag
        
        val firstName = childData["firstName"] as? String ?: ""
        val lastName = childData["lastName"] as? String ?: ""
        itemView.findViewById<TextView>(R.id.tvChildHeaderName).text = getString(CommonR.string.name_format, firstName, lastName)
        
        val content = itemView.findViewById<LinearLayout>(R.id.layoutChildContent)
        val chevron = itemView.findViewById<ImageView>(R.id.ivChildChevron)
        content.isVisible = true
        
        itemView.findViewById<LinearLayout>(R.id.btnToggleChildInfo).setOnClickListener {
            content.isVisible = !content.isVisible
            chevron.rotation = if (content.isVisible) 0f else -90f
        }

        itemView.findViewById<View>(R.id.btnChangeChildPhoto).setOnClickListener {
            currentChildTagForPhoto = tag
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickChildImageLauncher.launch(intent)
        }

        itemView.findViewById<View>(R.id.btnEditChildSuffix).setOnClickListener {
            showSuffixDialog(itemView.findViewById(R.id.tvChildSuffix))
        }
        itemView.findViewById<View>(R.id.btnEditChildGrade).setOnClickListener {
            showGradeDialog(itemView.findViewById(R.id.tvChildGrade))
        }
        itemView.findViewById<View>(R.id.btnEditChildBloodType).setOnClickListener {
            showBloodTypeDialog(itemView.findViewById(R.id.tvChildBloodType))
        }

        val childImg = itemView.findViewById<ImageView>(R.id.imgChild)
        val childAvatar = childrenUris[tag] ?: childData["childAvatarUrl"] as? String ?: ""
        Glide.with(this).load(childAvatar)
            .placeholder(CommonR.drawable.ic_person_placeholder)
            .circleCrop()
            .into(childImg)

        itemView.findViewById<EditText>(R.id.etChildFirstName).setText(firstName)
        itemView.findViewById<EditText>(R.id.etChildMiddleName).setText(childData["middleName"] as? String ?: "")
        itemView.findViewById<EditText>(R.id.etChildLastName).setText(lastName)
        itemView.findViewById<TextView>(R.id.tvChildSuffix).text = childData["suffix"] as? String ?: "NONE"
        itemView.findViewById<EditText>(R.id.etChildClass).setText((childData["class"] ?: childData["section"]) as? String ?: "")
        itemView.findViewById<TextView>(R.id.tvChildGrade).text = childData["grade"] as? String ?: "GRADE"
        itemView.findViewById<EditText>(R.id.etChildSchool).setText(childData["school"] as? String ?: "")
        itemView.findViewById<EditText>(R.id.etChildAddress).setText((childData["address"] ?: childData["homeAddress"]) as? String ?: "")
        itemView.findViewById<TextView>(R.id.tvChildBloodType).text = childData["bloodType"] as? String ?: "-"
        itemView.findViewById<EditText>(R.id.etChildAllergies).setText(childData["allergies"] as? String ?: "")
        itemView.findViewById<EditText>(R.id.etChildConditions).setText((childData["medicalConditions"] ?: childData["conditions"]) as? String ?: "")
        itemView.findViewById<EditText>(R.id.etChildMedications).setText((childData["currentMedications"] ?: childData["medications"]) as? String ?: "")
        
        container?.addView(itemView)
    }

    private fun saveAllChanges(view: View) {
        val saveBtn = view.findViewById<View>(R.id.btnSaveParentChanges)
        saveBtn.isEnabled = false
        Toast.makeText(context, "Updating profile...", Toast.LENGTH_SHORT).show()

        val uploadQueue = mutableListOf<Pair<String, Uri>>()
        parentAvatarUri?.let { uploadQueue.add("parent" to it) }
        childrenUris.forEach { (tag, uri) -> uploadQueue.add(tag to uri) }

        if (uploadQueue.isEmpty()) {
            performFirestoreUpdate(view, emptyMap())
        } else {
            uploadToCloudinary(uploadQueue, view)
        }
    }

    private fun uploadToCloudinary(queue: List<Pair<String, Uri>>, mainView: View) {
        val urls = mutableMapOf<String, String>()
        var completed = 0
        queue.forEach { (key, uri) ->
            MediaManager.get().upload(uri)
                .unsigned("buswatch_unsigned")
                .option("folder", "parents/${user.id}")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: MutableMap<Any?, Any?>?) {
                        urls[key] = resultData?.get("secure_url") as? String ?: ""
                        checkFinished()
                    }
                    override fun onError(requestId: String, error: ErrorInfo?) { checkFinished() }
                    override fun onReschedule(requestId: String, error: ErrorInfo?) { checkFinished() }

                    private fun checkFinished() {
                        completed++
                        if (completed == queue.size) performFirestoreUpdate(mainView, urls)
                    }
                }).dispatch()
        }
    }

    private fun performFirestoreUpdate(view: View, newUrls: Map<String, String>) {
        val firstName = view.findViewById<EditText>(R.id.etFirstName).text.toString()
        val lastName = view.findViewById<EditText>(R.id.etLastName).text.toString()
        val middleName = view.findViewById<EditText>(R.id.etMiddleName).text.toString()
        val suffix = view.findViewById<TextView>(R.id.tvSuffix).text.toString()
        val phone = view.findViewById<EditText>(R.id.etPhone).text.toString()

        val parentUpdate = mutableMapOf<String, Any>(
            "profile.firstName" to firstName,
            "profile.lastName" to lastName,
            "profile.middleName" to middleName,
            "profile.suffix" to suffix,
            "profile.phone" to phone
        )
        newUrls["parent"]?.let { parentUpdate["profile.parentAvatarUrl"] = it }

        val eName = view.findViewById<EditText>(R.id.etEmergencyName).text.toString()
        val ePhone = view.findViewById<EditText>(R.id.etEmergencyPhone).text.toString()
        val eRel = view.findViewById<EditText>(R.id.etRelationship).text.toString()
        parentUpdate["emergencyContacts"] = listOf(mapOf("name" to eName, "phone" to ePhone, "relationship" to eRel))

        val container = view.findViewById<LinearLayout>(R.id.layoutChildrenContainer)
        val additionalChildren = mutableListOf<Map<String, Any>>()
        val subCollectionUpdates = mutableMapOf<String, Map<String, Any>>()

        for (i in 0 until (container?.childCount ?: 0)) {
            val itemView = container?.getChildAt(i) ?: continue
            val tag = itemView.tag as? String ?: continue
            
            val original = originalChildrenData[tag] ?: emptyMap()
            val childData = original.toMutableMap()
            
            childData["firstName"] = itemView.findViewById<EditText>(R.id.etChildFirstName).text.toString()
            childData["middleName"] = itemView.findViewById<EditText>(R.id.etChildMiddleName).text.toString()
            childData["lastName"] = itemView.findViewById<EditText>(R.id.etChildLastName).text.toString()
            childData["suffix"] = itemView.findViewById<TextView>(R.id.tvChildSuffix).text.toString()
            childData["class"] = itemView.findViewById<EditText>(R.id.etChildClass).text.toString()
            childData["grade"] = itemView.findViewById<TextView>(R.id.tvChildGrade).text.toString()
            childData["school"] = itemView.findViewById<EditText>(R.id.etChildSchool).text.toString()
            childData["address"] = itemView.findViewById<EditText>(R.id.etChildAddress).text.toString()
            if (original.containsKey("homeAddress")) {
                childData["homeAddress"] = itemView.findViewById<EditText>(R.id.etChildAddress).text.toString()
            }
            childData["bloodType"] = itemView.findViewById<TextView>(R.id.tvChildBloodType).text.toString()
            childData["allergies"] = itemView.findViewById<EditText>(R.id.etChildAllergies).text.toString()
            childData["medicalConditions"] = itemView.findViewById<EditText>(R.id.etChildConditions).text.toString()
            childData["currentMedications"] = itemView.findViewById<EditText>(R.id.etChildMedications).text.toString()

            newUrls[tag]?.let { childData["childAvatarUrl"] = it }

            when {
                tag == "child" -> parentUpdate["child"] = childData
                tag.startsWith("children_") -> additionalChildren.add(childData)
                else -> subCollectionUpdates[tag] = childData
            }
        }

        if (additionalChildren.isNotEmpty()) {
            parentUpdate["children"] = additionalChildren
        }

        db.collection("parents").document(user.id).update(parentUpdate).addOnSuccessListener {
            if (subCollectionUpdates.isEmpty()) {
                onUpdateSuccess()
            } else {
                val batch = db.batch()
                subCollectionUpdates.forEach { (id, data) ->
                    val ref = db.collection("parents").document(user.id).collection("students").document(id)
                    batch.update(ref, data)
                }
                batch.commit().addOnSuccessListener { onUpdateSuccess() }.addOnFailureListener { onUpdateFailure() }
            }
        }.addOnFailureListener { onUpdateFailure() }
    }

    private fun onUpdateSuccess() {
        Toast.makeText(context, "Update successfully", Toast.LENGTH_LONG).show()
        (requireActivity() as? AdminHome)?.replaceFragment(UsersFragment())
    }

    private fun onUpdateFailure() {
        Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
        view?.findViewById<View>(R.id.btnSaveParentChanges)?.isEnabled = true
    }
}
