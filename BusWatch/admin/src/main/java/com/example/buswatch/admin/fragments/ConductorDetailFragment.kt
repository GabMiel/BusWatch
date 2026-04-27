package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore

class ConductorDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var user: UserAdmin
    private var onBack: (() -> Unit)? = null

    companion object {
        fun newInstance(user: UserAdmin, onBack: (() -> Unit)? = null) = ConductorDetailFragment().apply {
            this.user = user
            this.onBack = onBack
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_view_conductor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadData(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackConductorDetail)?.setOnClickListener { 
            onBack?.invoke() ?: (requireActivity() as? AdminHome)?.replaceFragment(ConductorsFragment())
        }
    }

    private fun loadData(view: View) {
        db.collection("conductors").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            view.findViewById<TextView>(R.id.tvFirstName).text = doc.getString("firstName") ?: ""
            view.findViewById<TextView>(R.id.tvMiddleName).text = doc.getString("middleName") ?: ""
            view.findViewById<TextView>(R.id.tvLastName).text = doc.getString("lastName") ?: ""
            view.findViewById<TextView>(R.id.tvSuffix).text = doc.getString("suffix") ?: ""
            view.findViewById<TextView>(R.id.tvEmail).text = doc.getString("email") ?: ""
            view.findViewById<TextView>(R.id.tvPhone).text = doc.getString("phone") ?: ""
            view.findViewById<TextView>(R.id.tvLanguage).text = doc.getString("language") ?: "English"
            
            val avatar = doc.getString("profilePhoto") ?: doc.getString("conductorAvatar") ?: ""
            if (avatar.isNotEmpty()) {
                Glide.with(this).load(avatar)
                    .placeholder(CommonR.drawable.ic_person_placeholder)
                    .circleCrop()
                    .into(view.findViewById(R.id.imgConductor))
            }
        }
    }
}
