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
        val onBackAction = { onBack?.invoke() }
        view.findViewById<ImageButton>(R.id.btnBackParentDetail)?.setOnClickListener { onBackAction() }
        view.findViewById<View>(R.id.btnBackParentAction)?.setOnClickListener { onBackAction() }
        
        view.findViewById<View>(R.id.btnEditParentAction)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.replaceFragment(ParentEditFragment.newInstance(user))
        }
        
        view.findViewById<LinearLayout>(R.id.layoutApprovalButtons).isVisible = user.status == "pending"
        view.findViewById<Button>(R.id.btnAccept).setOnClickListener { 
            (requireActivity() as? AdminHome)?.approveUserFromApprovalsInternal(user)
        }
        view.findViewById<Button>(R.id.btnReject).setOnClickListener { 
            (requireActivity() as? AdminHome)?.rejectUserFromApprovalsInternal(user)
        }
    }

    private fun loadData(view: View) {
        db.collection("parents").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            
            view.findViewById<TextView>(R.id.tvFirstName).text = doc.getString("firstName") ?: ""
            view.findViewById<TextView>(R.id.tvMiddleName).text = doc.getString("middleName") ?: ""
            view.findViewById<TextView>(R.id.tvLastName).text = doc.getString("lastName") ?: ""
            view.findViewById<TextView>(R.id.tvSuffix).text = doc.getString("suffix") ?: ""
            view.findViewById<TextView>(R.id.tvEmail).text = doc.getString("email") ?: ""
            view.findViewById<TextView>(R.id.tvPhone).text = doc.getString("phone") ?: ""
            
            view.findViewById<TextView>(R.id.tvEmergencyName).text = doc.getString("emergencyContactName") ?: "---"
            view.findViewById<TextView>(R.id.tvEmergencyPhone).text = doc.getString("emergencyContactPhone") ?: "---"
            view.findViewById<TextView>(R.id.tvRelationship).text = doc.getString("emergencyRelationship") ?: "---"
            
            val avatar = doc.getString("profilePhoto") ?: doc.getString("avatarUrl") ?: ""
            if (avatar.isNotEmpty()) {
                Glide.with(this).load(avatar)
                    .placeholder(CommonR.drawable.ic_person_placeholder)
                    .circleCrop()
                    .into(view.findViewById(R.id.imgParent))
            }
            
            doc.reference.collection("students").get().addOnSuccessListener { studentDocs ->
                val container = view.findViewById<LinearLayout>(R.id.layoutChildrenContainer)
                container?.removeAllViews()
                
                for (sDoc in studentDocs) {
                    val childData = sDoc.data
                    val childView = layoutInflater.inflate(R.layout.layout_view_child_item, container, false)
                    
                    val firstName = childData["firstName"] as? String ?: ""
                    val lastName = childData["lastName"] as? String ?: ""
                    childView.findViewById<TextView>(R.id.tvChildHeaderName).text = "$firstName $lastName".trim()
                    
                    childView.findViewById<TextView>(R.id.tvChildFirstName).text = firstName
                    childView.findViewById<TextView>(R.id.tvChildMiddleName).text = childData["middleName"] as? String ?: ""
                    childView.findViewById<TextView>(R.id.tvChildLastName).text = lastName
                    childView.findViewById<TextView>(R.id.tvChildSuffix).text = childData["suffix"] as? String ?: "None"
                    childView.findViewById<TextView>(R.id.tvChildSection).text = childData["section"] as? String ?: ""
                    childView.findViewById<TextView>(R.id.tvChildGrade).text = childData["grade"] as? String ?: ""
                    childView.findViewById<TextView>(R.id.tvChildSchool).text = childData["school"] as? String ?: ""
                    childView.findViewById<TextView>(R.id.tvChildAddress).text = childData["address"] as? String ?: ""
                    
                    childView.findViewById<TextView>(R.id.tvChildBloodType).text = childData["bloodType"] as? String ?: "---"
                    childView.findViewById<TextView>(R.id.tvChildAllergies).text = childData["allergies"] as? String ?: "None"
                    childView.findViewById<TextView>(R.id.tvChildConditions).text = childData["medicalConditions"] as? String ?: "None"
                    childView.findViewById<TextView>(R.id.tvChildMedications).text = childData["medications"] as? String ?: "None"
                    
                    val childAvatar = childData["avatarUrl"] as? String ?: ""
                    if (childAvatar.isNotEmpty()) {
                        Glide.with(this).load(childAvatar)
                            .placeholder(CommonR.drawable.ic_person_placeholder)
                            .circleCrop()
                            .into(childView.findViewById(R.id.imgChild))
                    }
                    
                    val content = childView.findViewById<LinearLayout>(R.id.layoutChildContent)
                    val chevron = childView.findViewById<ImageView>(R.id.ivChildChevron)
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
        }
    }
}
