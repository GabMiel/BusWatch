package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore

class ParentDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var user: UserAdmin
    private var onBack: (() -> Unit)? = null

    companion object {
        fun newInstance(user: UserAdmin, onBack: () -> Unit) = ParentDetailFragment().apply {
            this.user = user
            this.onBack = onBack
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_view_parent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadData(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackParentDetail)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.replaceFragment(UsersFragment())
        }

        view.findViewById<ImageButton>(R.id.btnEditParentAction)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.replaceFragment(ParentEditFragment.newInstance(user))
        }

        view.findViewById<LinearLayout>(R.id.layoutApprovalButtons).isVisible = user.status == "pending"
        view.findViewById<Button>(R.id.btnAccept)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.approveUserFromApprovalsInternal(user)
        }
        view.findViewById<Button>(R.id.btnReject)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.rejectUserFromApprovalsInternal(user)
        }
    }

    private fun loadData(view: View) {
        db.collection("parents").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            
            @Suppress("UNCHECKED_CAST")
            val profile = doc.get("profile") as? Map<String, Any>
            
            view.findViewById<TextView>(R.id.tvFirstName).text = profile?.get("firstName") as? String ?: ""
            view.findViewById<TextView>(R.id.tvMiddleName).text = profile?.get("middleName") as? String ?: ""
            view.findViewById<TextView>(R.id.tvLastName).text = profile?.get("lastName") as? String ?: ""
            view.findViewById<TextView>(R.id.tvSuffix).text = profile?.get("suffix") as? String ?: ""
            view.findViewById<TextView>(R.id.tvEmail).text = profile?.get("email") as? String ?: ""
            view.findViewById<TextView>(R.id.tvPhone).text = profile?.get("phone") as? String ?: ""
            
            @Suppress("UNCHECKED_CAST")
            val emergencyContacts = doc.get("emergencyContacts") as? List<Map<String, Any>>
            if (!emergencyContacts.isNullOrEmpty()) {
                val primaryContact = emergencyContacts[0]
                view.findViewById<TextView>(R.id.tvEmergencyName).text = primaryContact["name"] as? String ?: "---"
                view.findViewById<TextView>(R.id.tvEmergencyPhone).text = primaryContact["phone"] as? String ?: "---"
                view.findViewById<TextView>(R.id.tvRelationship).text = primaryContact["relationship"] as? String ?: "---"
            }
            
            val avatar = profile?.get("parentAvatarUrl") as? String ?: ""
            if (avatar.isNotEmpty()) {
                Glide.with(this).load(avatar)
                    .placeholder(CommonR.drawable.ic_person_placeholder)
                    .circleCrop()
                    .into(view.findViewById(R.id.imgParent))
            }
            
            val container = view.findViewById<LinearLayout>(R.id.layoutChildrenContainer)
            container?.removeAllViews()

            // Fetch primary child
            @Suppress("UNCHECKED_CAST")
            val primaryChild = doc.get("child") as? Map<String, Any>
            if (primaryChild != null) {
                addChildToView(primaryChild, container)
            }

            // Fetch additional children
            @Suppress("UNCHECKED_CAST")
            val additionalChildren = doc.get("children") as? List<Map<String, Any>>
            additionalChildren?.forEach { childData ->
                addChildToView(childData, container)
            }
        }
    }

    private fun addChildToView(childData: Map<String, Any>, container: LinearLayout?) {
        val childView = layoutInflater.inflate(R.layout.layout_view_child_item, container, false)
        
        val firstName = childData["firstName"] as? String ?: ""
        val lastName = childData["lastName"] as? String ?: ""
        childView.findViewById<TextView>(R.id.tvChildHeaderName).text = "$firstName $lastName".trim()
        
        childView.findViewById<TextView>(R.id.tvChildFirstName).text = firstName
        childView.findViewById<TextView>(R.id.tvChildMiddleName).text = childData["middleName"] as? String ?: ""
        childView.findViewById<TextView>(R.id.tvChildLastName).text = lastName
        childView.findViewById<TextView>(R.id.tvChildSuffix).text = childData["suffix"] as? String ?: "None"
        childView.findViewById<TextView>(R.id.tvChildSection).text = (childData["class"] ?: childData["section"]) as? String ?: ""
        childView.findViewById<TextView>(R.id.tvChildGrade).text = childData["grade"] as? String ?: ""
        childView.findViewById<TextView>(R.id.tvChildSchool).text = childData["school"] as? String ?: ""
        childView.findViewById<TextView>(R.id.tvChildAddress).text = (childData["address"] ?: childData["homeAddress"]) as? String ?: ""
        
        // Fetch and display stop name
        val stopId = childData["stop"] as? String ?: ""
        val tvStop = childView.findViewById<TextView>(R.id.tvChildStop)
        if (stopId.isNotEmpty()) {
            db.collection("stops").document(stopId).get().addOnSuccessListener { sDoc ->
                if (isAdded && sDoc.exists()) {
                    tvStop.text = sDoc.getString("name") ?: getString(CommonR.string.not_assigned_label)
                }
            }
        } else {
            tvStop.text = getString(CommonR.string.not_assigned_label)
        }

        childView.findViewById<TextView>(R.id.tvChildBloodType).text = childData["bloodType"] as? String ?: "---"
        childView.findViewById<TextView>(R.id.tvChildAllergies).text = childData["allergies"] as? String ?: "None"
        childView.findViewById<TextView>(R.id.tvChildConditions).text = (childData["medicalConditions"] ?: childData["conditions"]) as? String ?: "None"
        childView.findViewById<TextView>(R.id.tvChildMedications).text = (childData["medications"] ?: childData["currentMedications"]) as? String ?: "None"
        
        val childAvatar = childData["childAvatarUrl"] as? String ?: ""
        if (childAvatar.isNotEmpty()) {
            Glide.with(this).load(childAvatar)
                .placeholder(CommonR.drawable.ic_person_placeholder)
                .circleCrop()
                .into(childView.findViewById(R.id.imgChild))
        }
        
        val content = childView.findViewById<LinearLayout>(R.id.layoutChildContent)
        val chevron = childView.findViewById<ImageView>(R.id.ivChildChevron)
        
        // Show content by default
        content.visibility = View.VISIBLE
        chevron.rotation = 0f
        
        childView.findViewById<View>(R.id.btnToggleChildInfo).setOnClickListener {
            if (content.visibility == View.VISIBLE) {
                content.visibility = View.GONE
                chevron.rotation = -90f
            } else {
                content.visibility = View.VISIBLE
                chevron.rotation = 0f
            }
        }
        
        container?.addView(childView)
    }
}
