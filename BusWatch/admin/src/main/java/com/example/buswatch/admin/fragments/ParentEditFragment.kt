package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore

class ParentEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var user: UserAdmin

    companion object {
        fun newInstance(user: UserAdmin) = ParentEditFragment().apply {
            this.user = user
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
            (requireActivity() as? AdminHome)?.replaceFragment(ParentDetailFragment.newInstance(user) { 
                (requireActivity() as? AdminHome)?.loadUsers()
            })
        }
        
        view.findViewById<View>(R.id.btnSaveParentChanges)?.setOnClickListener { 
            // Save logic here or call activity
            (requireActivity() as? AdminHome)?.loadUsers()
        }
        
        view.findViewById<View>(R.id.btnCancelParentChanges)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.replaceFragment(ParentDetailFragment.newInstance(user) { 
                (requireActivity() as? AdminHome)?.loadUsers()
            })
        }
    }

    private fun loadData(view: View) {
        val childrenContainer = view.findViewById<LinearLayout>(R.id.layoutChildrenContainer)
        db.collection("parents").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            @Suppress("UNCHECKED_CAST")
            val profile = doc.get("profile") as? Map<String, Any>
            
            view.findViewById<EditText>(R.id.etFirstName).setText(profile?.get("firstName") as? String ?: doc.getString("firstName") ?: "")
            view.findViewById<EditText>(R.id.etLastName).setText(profile?.get("lastName") as? String ?: doc.getString("lastName") ?: "")
            view.findViewById<EditText>(R.id.etPhone).setText(profile?.get("phone") as? String ?: doc.getString("phone") ?: "")
            
            val avatar = profile?.get("avatarUrl") as? String ?: doc.getString("avatarUrl") ?: ""
            if (avatar.isNotEmpty()) {
                Glide.with(this).load(avatar)
                    .placeholder(CommonR.drawable.ic_person_placeholder)
                    .circleCrop()
                    .into(view.findViewById(R.id.imgParent))
            }

            doc.reference.collection("students").get().addOnSuccessListener { studentDocs ->
                childrenContainer?.removeAllViews()
                for (studentDoc in studentDocs) {
                    val childData = studentDoc.data
                    val itemView = layoutInflater.inflate(R.layout.layout_edit_child_item, childrenContainer, false)
                    itemView.findViewById<TextView>(R.id.tvChildHeaderName).text = getString(CommonR.string.name_format, childData["firstName"], childData["lastName"])
                    
                    val content = itemView.findViewById<LinearLayout>(R.id.layoutChildContent)
                    val chevron = itemView.findViewById<ImageView>(R.id.ivChildChevron)
                    itemView.findViewById<LinearLayout>(R.id.btnToggleChildInfo).setOnClickListener {
                        if (content.visibility == View.VISIBLE) { 
                            content.visibility = View.GONE
                            chevron.rotation = -90f 
                        } else { 
                            content.visibility = View.VISIBLE
                            chevron.rotation = 0f 
                        }
                    }
                    itemView.findViewById<EditText>(R.id.etChildFirstName).setText(childData["firstName"] as? String ?: "")
                    itemView.findViewById<EditText>(R.id.etChildLastName).setText(childData["lastName"] as? String ?: "")
                    childrenContainer?.addView(itemView)
                }
            }
        }
    }
}
