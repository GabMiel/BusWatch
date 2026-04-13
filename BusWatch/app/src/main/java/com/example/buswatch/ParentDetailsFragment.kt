package com.example.buswatch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.collections.Map as KMap

class ParentDetailsFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var childrenList = mutableListOf<ChildDetail>()
    private lateinit var adapter: DetailsChildAdapter
    private var isDeleteMode = false

    private var parentListener: ListenerRegistration? = null

    private var tempAvatarUri: Uri? = null

    private var dialogAvatarView: ImageView? = null
    private var tvPhotoRequired: TextView? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var currentMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var etAddChildAddress: EditText? = null

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            tempAvatarUri = it
            showPreviewDialog(it)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_parent_details, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        Configuration.getInstance().load(requireContext(), requireActivity().getSharedPreferences("osmdroid", AppCompatActivity.MODE_PRIVATE))

        view.findViewById<View>(R.id.btnParentsEdit).setOnClickListener {
            showEditProfileDialog()
        }

        view.findViewById<ImageButton>(R.id.btnAddChild).setOnClickListener {
            showAddChildDialog()
        }

        view.findViewById<ImageButton>(R.id.btnEmergencyEdit).setOnClickListener {
            showEditEmergencyDialog()
        }

        val btnConfirmDelete = view.findViewById<Button>(R.id.btnConfirmDeleteChildren)
        view.findViewById<ImageButton>(R.id.btnDeleteChild).setOnClickListener {
            if (childrenList.isEmpty()) {
                Toast.makeText(requireContext(), "You need to add a child first before you can use the removal feature", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isDeleteMode = !isDeleteMode
            adapter.setDeleteMode(isDeleteMode)
            btnConfirmDelete.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
            if (isDeleteMode) {
                Toast.makeText(requireContext(), "Select the children you wish to remove from your list", Toast.LENGTH_SHORT).show()
            }
        }

        btnConfirmDelete.setOnClickListener {
            val selectedChildren = adapter.getSelectedChildren()
            if (selectedChildren.isEmpty()) {
                isDeleteMode = false
                adapter.setDeleteMode(false)
                btnConfirmDelete.visibility = View.GONE
                Toast.makeText(requireContext(), "Removal cancelled: No children were selected", Toast.LENGTH_SHORT).show()
            } else if (selectedChildren.size >= childrenList.size) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Action Restricted")
                    .setMessage("At least one child must remain linked to your account. If you wish to replace this child, please add the new one first before removing the current one.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                showBulkDeleteWarning(selectedChildren)
            }
        }

        setupRecyclerView(view)
        startParentDataListener(view)

        return view
    }

    private fun setupRecyclerView(view: View) {
        val rvChildren = view.findViewById<RecyclerView>(R.id.rvDetailsChildren)
        rvChildren.layoutManager = LinearLayoutManager(requireContext())
        adapter = DetailsChildAdapter(
            childrenList,
            onViewClick = { child ->
                val intent = Intent(requireContext(), StudentDetailsActivity::class.java)
                intent.putExtra("childName", child.name)
                startActivity(intent)
            }
        )
        rvChildren.adapter = adapter

        val rvContacts = view.findViewById<RecyclerView>(R.id.rvDetailsContacts)
        rvContacts.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun startParentDataListener(view: View) {
        val uid = auth.currentUser?.uid ?: return

        parentListener?.remove()
        parentListener = db.collection("parents").document(uid)
            .addSnapshotListener { document, e ->
                if (e != null) {
                    Log.w("ParentDetails", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (isAdded && document != null && document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val profile = document.get("profile") as? KMap<String, Any>

                    val fName = profile?.get("firstName") as? String ?: ""
                    val lName = profile?.get("lastName") as? String ?: ""
                    val fullName = "$fName $lName".trim()

                    view.findViewById<TextView>(R.id.tvHeaderSub).text = fullName
                    view.findViewById<TextView>(R.id.tvParentFullName).text = fullName
                    view.findViewById<TextView>(R.id.tvParentEmail).text = profile?.get("email") as? String ?: ""
                    view.findViewById<TextView>(R.id.tvParentPhone).text = profile?.get("phone") as? String ?: ""

                    val parentAvatarUrl = profile?.get("parentAvatarUrl") as? String ?: ""

                    val ivParentAvatar = view.findViewById<ImageView>(R.id.imgParentAvatar)
                    if (parentAvatarUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(parentAvatarUrl)
                            .placeholder(CommonR.drawable.user)
                            .error(CommonR.drawable.user)
                            .circleCrop()
                            .into(ivParentAvatar)
                    } else {
                        ivParentAvatar.setImageResource(CommonR.drawable.user)
                    }

                    val tvStatus = view.findViewById<TextView>(R.id.tvParentStatus)
                    val status = document.getString("status") ?: "pending"
                    tvStatus.text = status.uppercase()

                    if (status.lowercase() == "approved") {
                        tvStatus.backgroundTintList = ColorStateList.valueOf("#D4EDDA".toColorInt())
                        tvStatus.setTextColor("#155724".toColorInt())
                    } else {
                        tvStatus.backgroundTintList = ColorStateList.valueOf("#FFF3CD".toColorInt())
                        tvStatus.setTextColor("#856404".toColorInt())
                    }

                    val newChildrenList = mutableListOf<ChildDetail>()
                    val childData = document.get("child")
                    if (childData is KMap<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        newChildrenList.add(mapToChildDetail(childData as KMap<String, Any>, "primary_child"))
                    }

                    val childrenListData = document.get("children")
                    if (childrenListData is List<*>) {
                        childrenListData.forEachIndexed { index, item ->
                            if (item is KMap<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                newChildrenList.add(mapToChildDetail(item as KMap<String, Any>, index.toString()))
                            }
                        }
                    }

                    childrenList = newChildrenList
                    adapter.updateList(childrenList)

                    val contactList = mutableListOf<ContactDetail>()

                    // Add primary parent contact
                    val pPhone = profile?.get("phone") as? String ?: ""
                    if (fullName.isNotEmpty() || pPhone.isNotEmpty()) {
                        contactList.add(ContactDetail("Primary Parent", pPhone, fullName, "Parent"))
                    }

                    val contactsData = document.get("emergencyContacts")
                    if (contactsData is List<*>) {
                        contactsData.forEach { item ->
                            if (item is KMap<*, *>) {
                                val name = item["name"] as? String ?: ""
                                val cPhone = item["phone"] as? String ?: ""
                                val relationship = item["relationship"] as? String ?: ""
                                if (name.isNotEmpty() || cPhone.isNotEmpty()) {
                                    contactList.add(ContactDetail("Emergency Contact", cPhone, name, relationship))
                                }
                            }
                        }
                    }

                    val rvContacts = view.findViewById<RecyclerView>(R.id.rvDetailsContacts)
                    rvContacts.adapter = DetailsContactAdapter(contactList)
                }
            }
    }


    private fun mapToChildDetail(map: KMap<String, Any>, id: String): ChildDetail {
        val cfName = map["firstName"] as? String ?: ""
        val clName = map["lastName"] as? String ?: ""
        val cmName = map["middleName"] as? String ?: ""
        val csuffix = map["suffix"] as? String ?: ""
        val age = map["age"]?.toString() ?: ""

        return ChildDetail(
            name = "$cfName $clName".trim(),
            grade = map["grade"] as? String ?: "",
            school = map["school"] as? String ?: "The Immaculate Mother Academy Inc.",
            status = map["status"] as? String ?: "AT HOME",
            avatarUrl = map["childAvatarUrl"] as? String ?: "",
            firstName = cfName,
            lastName = clName,
            middleName = cmName,
            suffix = csuffix,
            age = age,
            section = map["class"] as? String ?: map["section"] as? String ?: "",
            id = id,
            originalData = map
        )
    }

    private fun uploadParentAvatar(uid: String, uri: Uri) {
        val path = "parents/$uid/parent_avatar.jpg"
        val ref = storage.reference.child(path)

        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    db.collection("parents").document(uid)
                        .update("profile.parentAvatarUrl", url.toString())
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Parent photo updated!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Error saving photo: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun showAddChildDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_child, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        tempAvatarUri = null
        selectedLatitude = null
        selectedLongitude = null
        currentMarker = null
        dialogAvatarView = dialogView.findViewById(R.id.ivAddChildAvatar)
        tvPhotoRequired = dialogView.findViewById(R.id.tvAddChildPhotoRequired)
        etAddChildAddress = dialogView.findViewById(R.id.etAddChildAddress)

        val suffixSelector = dialogView.findViewById<FrameLayout>(R.id.btnAddChildSuffix)
        val tvSelectedSuffix = dialogView.findViewById<TextView>(R.id.tvAddChildSelectedSuffix)
        var selectedSuffix = ""

        suffixSelector.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(requireContext())
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, pos ->
                    selectedSuffix = if (pos == 0) "" else suffixes[pos]
                    tvSelectedSuffix.text = if (pos == 0) getString(CommonR.string.suffix) else suffixes[pos]
                    tvSelectedSuffix.setTextColor(if (pos == 0) "#888888".toColorInt() else Color.BLACK)
                }
                .show()
        }

        val btnGrade = dialogView.findViewById<FrameLayout>(R.id.btnAddChildGrade)
        val tvGrade = dialogView.findViewById<TextView>(R.id.tvAddChildSelectedGrade)
        var selectedGrade = ""
        btnGrade.setOnClickListener {
            val grades = arrayOf("Nursery", "Kinder", "Prep", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6")
            AlertDialog.Builder(requireContext())
                .setTitle("Select Grade")
                .setItems(grades) { _, pos ->
                    selectedGrade = grades[pos]
                    tvGrade.text = selectedGrade
                    tvGrade.setTextColor(Color.BLACK)
                }
                .show()
        }

        val btnBloodType = dialogView.findViewById<FrameLayout>(R.id.btnAddChildBloodType)
        val tvBloodType = dialogView.findViewById<TextView>(R.id.tvAddChildSelectedBloodType)
        var selectedBloodType = ""
        btnBloodType.setOnClickListener {
            val bloodTypes = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown")
            AlertDialog.Builder(requireContext())
                .setTitle("Select Blood Type")
                .setItems(bloodTypes) { _, pos ->
                    selectedBloodType = bloodTypes[pos]
                    tvBloodType.text = selectedBloodType
                    tvBloodType.setTextColor(Color.BLACK)
                }
                .show()
        }

        val mapView = dialogView.findViewById<MapView>(R.id.mapAddChild)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        val mapController = mapView.controller
        mapController.setZoom(17.0)
        mapController.setCenter(GeoPoint(14.7566, 121.0450))

        val receive = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    updateDialogMarker(it, mapView)
                    getAddressFromLocation(it.latitude, it.longitude)
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        mapView.overlays.add(MapEventsOverlay(receive))

        dialogView.findViewById<ImageButton>(R.id.btnAddChildMyLocation).setOnClickListener {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                    loc?.let {
                        val gp = GeoPoint(it.latitude, it.longitude)
                        mapController.animateTo(gp)
                        updateDialogMarker(gp, mapView)
                        getAddressFromLocation(it.latitude, it.longitude)
                    }
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        val avatarClickAction = View.OnClickListener {
            if (tempAvatarUri != null) {
                showPreviewDialog(tempAvatarUri!!)
            } else {
                pickAvatarLauncher.launch("image/*")
            }
        }

        dialogView.findViewById<View>(R.id.viewAddChildAvatarBg).setOnClickListener(avatarClickAction)
        dialogAvatarView?.setOnClickListener(avatarClickAction)
        dialogView.findViewById<ImageButton>(R.id.btnAddChildPhoto).setOnClickListener { pickAvatarLauncher.launch("image/*") }

        dialogView.findViewById<ImageButton>(R.id.btnDismissAddChild).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<Button>(R.id.btnAddChildConfirm).setOnClickListener { btn ->
            val fName = dialogView.findViewById<EditText>(R.id.etAddChildFirstName).text.toString().trim()
            val lName = dialogView.findViewById<EditText>(R.id.etAddChildLastName).text.toString().trim()
            val mName = dialogView.findViewById<EditText>(R.id.etAddChildMiddleName).text.toString().trim()
            val age = dialogView.findViewById<EditText>(R.id.etAddChildAge).text.toString().trim()
            val section = dialogView.findViewById<EditText>(R.id.etAddChildSection).text.toString().trim()
            val school = dialogView.findViewById<EditText>(R.id.etAddChildSchool).text.toString().trim()
            val address = etAddChildAddress?.text.toString().trim()

            val allergies = dialogView.findViewById<EditText>(R.id.etAddChildAllergies).text.toString().trim()
            val medications = dialogView.findViewById<EditText>(R.id.etAddChildMedications).text.toString().trim()
            val conditions = dialogView.findViewById<EditText>(R.id.etAddChildConditions).text.toString().trim()

            if (fName.isEmpty() || lName.isEmpty() || age.isEmpty() || section.isEmpty() || school.isEmpty() || address.isEmpty() || selectedGrade.isEmpty() || tempAvatarUri == null || selectedLatitude == null) {
                Toast.makeText(requireContext(), "Please fill in all required fields and upload photo (*)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btn.isEnabled = false
            uploadNewChildFull(fName, lName, mName, selectedSuffix, age, section, school, selectedGrade, address, selectedLatitude!!, selectedLongitude!!, selectedBloodType, allergies, medications, conditions, dialog)
        }
        dialog.show()
    }

    private fun updateDialogMarker(p: GeoPoint, mapView: MapView) {
        if (currentMarker == null) {
            currentMarker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Home Location"
            }
            mapView.overlays.add(currentMarker)
        }
        currentMarker?.position = p
        selectedLatitude = p.latitude
        selectedLongitude = p.longitude
        mapView.invalidate()
    }

    private fun getAddressFromLocation(lat: Double, lon: Double) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0].getAddressLine(0)
                        requireActivity().runOnUiThread {
                            etAddChildAddress?.setText(address)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    etAddChildAddress?.setText(addresses[0].getAddressLine(0))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uploadNewChildFull(
        fName: String, lName: String, mName: String, suffix: String,
        age: String, section: String, school: String, grade: String,
        address: String, lat: Double, lon: Double, blood: String,
        allergies: String, meds: String, conds: String, dialog: AlertDialog
    ) {
        val uid = auth.currentUser?.uid ?: return
        val uploadUri = tempAvatarUri ?: return

        val streamExists = try {
            requireContext().contentResolver.openInputStream(uploadUri)?.use { true } ?: false
        } catch (_: Exception) { false }

        if (!streamExists) {
            Toast.makeText(requireContext(), "Error: Image file not found or inaccessible", Toast.LENGTH_SHORT).show()
            dialog.findViewById<Button>(R.id.btnAddChildConfirm)?.isEnabled = true
            return
        }

        val ts = System.currentTimeMillis()
        val cleanedFName = fName.filter { it.isLetterOrDigit() }
        val path = "parents/$uid/child_${cleanedFName}_${ts}.jpg"
        val ref = storage.reference.child(path)

        Toast.makeText(requireContext(), "Adding child profile...", Toast.LENGTH_SHORT).show()

        ref.putFile(uploadUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    val newChild = mapOf(
                        "firstName" to fName,
                        "lastName" to lName,
                        "middleName" to mName,
                        "suffix" to suffix,
                        "age" to age,
                        "class" to section,
                        "school" to school,
                        "grade" to grade,
                        "address" to address,
                        "latitude" to lat,
                        "longitude" to lon,
                        "childAvatarUrl" to url.toString(),
                        "avatarPath" to path,
                        "bloodType" to blood,
                        "allergies" to allergies,
                        "medications" to meds,
                        "conditions" to conds,
                        "status" to "AT HOME"
                    )

                    db.collection("parents").document(uid).set(
                        mapOf("children" to FieldValue.arrayUnion(newChild)),
                        SetOptions.merge()
                    ).addOnSuccessListener {
                        Toast.makeText(requireContext(), "Child profile added successfully!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }.addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Database Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        dialog.findViewById<Button>(R.id.btnAddChildConfirm)?.isEnabled = true
                    }
                }.addOnFailureListener { e ->
                    Log.e("UploadDebug", "Download URL failed", e)
                    Toast.makeText(requireContext(), "Could not get photo URL: ${e.message}", Toast.LENGTH_SHORT).show()
                    dialog.findViewById<Button>(R.id.btnAddChildConfirm)?.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                Log.e("UploadDebug", "Upload failed", e)
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                dialog.findViewById<Button>(R.id.btnAddChildConfirm)?.isEnabled = true
            }
    }


    private fun showPreviewDialog(uri: Uri) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_photo, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ivAvatarPreview = dialogView.findViewById<ImageView>(R.id.ivPreviewAvatar)
        val cvAvatar = dialogView.findViewById<View>(R.id.cvAvatarPreview)
        val btnDelete = dialogView.findViewById<ImageButton>(R.id.btnDeletePhoto)

        cvAvatar.visibility = View.VISIBLE
        Glide.with(this).load(uri).circleCrop().into(ivAvatarPreview)

        btnDelete.setOnClickListener {
            dialog.dismiss()
            tempAvatarUri = null
            dialogAvatarView?.setImageResource(CommonR.drawable.user)
            tvPhotoRequired?.visibility = View.VISIBLE
        }
        dialogView.findViewById<Button>(R.id.btnPreviewCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnPreviewSave).setOnClickListener {
            dialog.dismiss()
            tempAvatarUri = uri
            Glide.with(this).load(uri).circleCrop().into(dialogAvatarView!!)
            tvPhotoRequired?.visibility = View.GONE
        }
        dialog.show()
    }

    private fun showBulkDeleteWarning(selectedChildren: List<ChildDetail>) {
        val names = selectedChildren.joinToString(", ") { it.name }
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Removal")
            .setMessage("Are you sure you want to remove the following children from your profile: $names?")
            .setPositiveButton("Remove") { _, _ ->
                deleteMultipleChildren(selectedChildren)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMultipleChildren(selectedChildren: List<ChildDetail>) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        val childrenToRemove = selectedChildren.filter { it.id != "primary_child" }.mapNotNull { child ->
            child.originalData
        }

        val hasPrimary = selectedChildren.any { it.id == "primary_child" }

        if (childrenToRemove.isNotEmpty()) {
            docRef.update("children", FieldValue.arrayRemove(*childrenToRemove.toTypedArray()))
                .addOnSuccessListener {
                    if (hasPrimary) {
                        deletePrimaryChild(uid)
                    } else {
                        onDeletionComplete()
                    }
                }
        } else if (hasPrimary) {
            deletePrimaryChild(uid)
        }
    }

    private fun deletePrimaryChild(uid: String) {
        db.collection("parents").document(uid).update("child", FieldValue.delete())
            .addOnSuccessListener {
                onDeletionComplete()
            }
    }

    private fun onDeletionComplete() {
        Toast.makeText(requireContext(), "The selected child profiles have been removed", Toast.LENGTH_SHORT).show()
        isDeleteMode = false
        adapter.setDeleteMode(false)
        view?.findViewById<Button>(R.id.btnConfirmDeleteChildren)?.visibility = View.GONE
    }

    private fun showEditProfileDialog() {
        val uid = auth.currentUser?.uid ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()

        tempAvatarUri = null
        dialogAvatarView = dialogView.findViewById(R.id.imgEditProfileAvatar)
        tvPhotoRequired = null

        val etFirstName = dialogView.findViewById<EditText>(R.id.etEditFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etEditLastName)
        val etMiddleName = dialogView.findViewById<EditText>(R.id.etEditMiddleName)
        val suffixSelector = dialogView.findViewById<FrameLayout>(R.id.btnEditProfileSuffix)
        val tvSelectedSuffix = dialogView.findViewById<TextView>(R.id.tvEditProfileSelectedSuffix)
        var selectedSuffix = ""

        val etEmail = dialogView.findViewById<EditText>(R.id.etEditEmail)
        val etPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)

        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val profile = doc.get("profile") as? KMap<String, Any>

            etFirstName.setText(profile?.get("firstName") as? String ?: "")
            etLastName.setText(profile?.get("lastName") as? String ?: "")
            etMiddleName.setText(profile?.get("middleName") as? String ?: "")

            selectedSuffix = profile?.get("suffix") as? String ?: ""
            if (selectedSuffix.isNotEmpty()) {
                tvSelectedSuffix.text = selectedSuffix
                tvSelectedSuffix.setTextColor(Color.BLACK)
            } else {
                tvSelectedSuffix.text = getString(CommonR.string.suffix)
                tvSelectedSuffix.setTextColor("#888888".toColorInt())
            }

            etEmail.setText(profile?.get("email") as? String ?: "")
            etPhone.setText(profile?.get("phone") as? String ?: "")

            val avatarUrl = profile?.get("parentAvatarUrl") as? String ?: ""

            if (avatarUrl.isNotEmpty()) {
                Glide.with(this).load(avatarUrl).placeholder(CommonR.drawable.user).error(CommonR.drawable.user).circleCrop().into(dialogAvatarView!!)
            } else {
                Glide.with(this).load(CommonR.drawable.user).circleCrop().into(dialogAvatarView!!)
            }
        }

        suffixSelector.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(requireContext())
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, pos ->
                    selectedSuffix = if (pos == 0) "" else suffixes[pos]
                    tvSelectedSuffix.text = if (pos == 0) getString(CommonR.string.suffix) else suffixes[pos]
                    tvSelectedSuffix.setTextColor(if (pos == 0) "#888888".toColorInt() else Color.BLACK)
                }
                .show()
        }

        val avatarClickAction = View.OnClickListener {
            if (tempAvatarUri != null) {
                showPreviewDialog(tempAvatarUri!!)
            } else {
                pickAvatarLauncher.launch("image/*")
            }
        }

        dialogView.findViewById<View>(R.id.rlEditProfileAvatar).setOnClickListener(avatarClickAction)
        dialogAvatarView?.setOnClickListener(avatarClickAction)
        dialogView.findViewById<View>(R.id.btnChangeProfilePhoto).setOnClickListener { pickAvatarLauncher.launch("image/*") }

        dialogView.findViewById<ImageButton>(R.id.btnDismissEditProfile).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener { dialog.dismiss() }

        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveProfile)
        btnSave.setOnClickListener {
            val updates = mutableMapOf<String, Any>(
                "profile.firstName" to etFirstName.text.toString().trim(),
                "profile.lastName" to etLastName.text.toString().trim(),
                "profile.middleName" to etMiddleName.text.toString().trim(),
                "profile.suffix" to selectedSuffix,
                "profile.email" to etEmail.text.toString().trim(),
                "profile.phone" to etPhone.text.toString().trim()
            )

            btnSave.isEnabled = false
            Toast.makeText(requireContext(), "Updating profile...", Toast.LENGTH_SHORT).show()

            if (tempAvatarUri != null) {
                uploadParentAvatar(tempAvatarUri!!, updates, dialog)
            } else {
                db.collection("parents").document(uid).update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                    }
            }
        }
        dialog.show()
    }


    private fun showEditEmergencyDialog() {
        val uid = auth.currentUser?.uid ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_student_emergency, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        val etParentFName = dialogView.findViewById<EditText>(R.id.etEditParentFirstName)
        val etParentLName = dialogView.findViewById<EditText>(R.id.etEditParentLastName)
        val etParentEmail = dialogView.findViewById<EditText>(R.id.etEditParentEmail)
        val etParentPhone = dialogView.findViewById<EditText>(R.id.etEditParentPhone)

        val etC1Name = dialogView.findViewById<EditText>(R.id.etEditContact1Name)
        val etC1Rel = dialogView.findViewById<EditText>(R.id.etEditContact1Rel)
        val etC1Email = dialogView.findViewById<EditText>(R.id.etEditContact1Email)
        val etC1Phone = dialogView.findViewById<EditText>(R.id.etEditContact1Phone)

        val etC2Name = dialogView.findViewById<EditText>(R.id.etEditContact2Name)
        val etC2Rel = dialogView.findViewById<EditText>(R.id.etEditContact2Rel)
        val etC2Email = dialogView.findViewById<EditText>(R.id.etEditContact2Email)
        val etC2Phone = dialogView.findViewById<EditText>(R.id.etEditContact2Phone)

        db.collection("parents").document(uid).get().addOnSuccessListener { document ->
            if (!document.exists()) return@addOnSuccessListener

            @Suppress("UNCHECKED_CAST")
            val profile = document.get("profile") as? KMap<String, Any>
            etParentFName.setText(profile?.get("firstName") as? String ?: "")
            etParentLName.setText(profile?.get("lastName") as? String ?: "")
            etParentEmail.setText(profile?.get("email") as? String ?: "")
            etParentPhone.setText(profile?.get("phone") as? String ?: "")

            @Suppress("UNCHECKED_CAST")
            val contacts = document.get("emergencyContacts") as? List<KMap<String, Any>> ?: emptyList()
            if (contacts.isNotEmpty()) {
                etC1Name.setText(contacts[0]["name"] as? String ?: "")
                etC1Rel.setText(contacts[0]["relationship"] as? String ?: "")
                etC1Email.setText(contacts[0]["email"] as? String ?: "")
                etC1Phone.setText(contacts[0]["phone"] as? String ?: "")
            }
            if (contacts.size > 1) {
                etC2Name.setText(contacts[1]["name"] as? String ?: "")
                etC2Rel.setText(contacts[1]["relationship"] as? String ?: "")
                etC2Email.setText(contacts[1]["email"] as? String ?: "")
                etC2Phone.setText(contacts[1]["phone"] as? String ?: "")
            }
        }

        dialogView.findViewById<ImageButton>(R.id.btnDismissEditEmergency).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener { dialog.dismiss() }

        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveEmergency)
        btnSave.setOnClickListener {
            val updatedContacts = mutableListOf<KMap<String, String>>()
            if (etC1Name.text.isNotEmpty()) {
                updatedContacts.add(mapOf(
                    "name" to etC1Name.text.toString().trim(),
                    "relationship" to etC1Rel.text.toString().trim(),
                    "email" to etC1Email.text.toString().trim(),
                    "phone" to etC1Phone.text.toString().trim()
                ))
            }
            if (etC2Name.text.isNotEmpty()) {
                updatedContacts.add(mapOf(
                    "name" to etC2Name.text.toString().trim(),
                    "relationship" to etC2Rel.text.toString().trim(),
                    "email" to etC2Email.text.toString().trim(),
                    "phone" to etC2Phone.text.toString().trim()
                ))
            }

            val updates = mutableMapOf<String, Any>(
                "emergencyContacts" to updatedContacts,
                "profile.firstName" to etParentFName.text.toString().trim(),
                "profile.lastName" to etParentLName.text.toString().trim(),
                "profile.email" to etParentEmail.text.toString().trim(),
                "profile.phone" to etParentPhone.text.toString().trim()
            )

            btnSave.isEnabled = false
            db.collection("parents").document(uid).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Emergency contacts updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                }
        }
        dialog.show()
    }

    private fun uploadParentAvatar(uploadUri: Uri, updates: MutableMap<String, Any>? = null, dialog: AlertDialog? = null) {
        val uid = auth.currentUser?.uid ?: return

        val streamExists = try {
            requireContext().contentResolver.openInputStream(uploadUri)?.use { true } ?: false
        } catch (_: Exception) { false }

        if (!streamExists) {
            Toast.makeText(requireContext(), "Error: Image file not found or inaccessible", Toast.LENGTH_SHORT).show()
            dialog?.findViewById<Button>(R.id.btnSaveProfile)?.isEnabled = true
            return
        }

        val ref = storage.reference.child("parents/$uid/parent_avatar.jpg")
        Toast.makeText(requireContext(), "Uploading parent photo...", Toast.LENGTH_SHORT).show()

        ref.putFile(uploadUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    val finalUpdates = updates ?: mutableMapOf()
                    finalUpdates["profile.parentAvatarUrl"] = url.toString()
                    finalUpdates["profile.parentAvatarPath"] = "parents/$uid/parent_avatar.jpg"

                    db.collection("parents").document(uid)
                        .update(finalUpdates)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Parent photo updated successfully!", Toast.LENGTH_SHORT).show()
                            dialog?.dismiss()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Database Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            dialog?.findViewById<Button>(R.id.btnSaveProfile)?.isEnabled = true
                            dialog?.findViewById<Button>(R.id.btnAddChildConfirm)?.isEnabled = true
                        }
                }.addOnFailureListener { e ->
                    Log.e("UploadDebug", "Download URL failed", e)
                    Toast.makeText(requireContext(), "Could not get photo URL: ${e.message}", Toast.LENGTH_SHORT).show()
                    dialog?.findViewById<Button>(R.id.btnSaveProfile)?.isEnabled = true
                    dialog?.findViewById<Button>(R.id.btnAddChildConfirm)?.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                Log.e("UploadDebug", "Upload failed", e)
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                dialog?.findViewById<Button>(R.id.btnSaveProfile)?.isEnabled = true
                dialog?.findViewById<Button>(R.id.btnAddChildConfirm)?.isEnabled = true
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        parentListener?.remove()
    }
}
