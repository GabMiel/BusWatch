package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.Emergency
import com.example.buswatch.admin.EmergencyAdapter
import com.example.buswatch.admin.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class EmergenciesFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private var emergencyListener: ListenerRegistration? = null
    private lateinit var adapter: EmergencyAdapter
    private var emergencyList = mutableListOf<Emergency>()
    private lateinit var tvNoEmergencies: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Listen for results from EmergencyDetailFragment
        parentFragmentManager.setFragmentResultListener("emergency_request", this) { _, bundle ->
            if (bundle.getBoolean("refresh", false)) {
                // Force a re-fetch when returning from a resolved emergency
                listenForEmergencies()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_emergencies, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerEmergencies)
        tvNoEmergencies = view.findViewById(R.id.tvNoEmergencies)

        // Reset the list and adapter immediately to avoid showing stale items from backstack
        emergencyList.clear()
        adapter = EmergencyAdapter(
            emergencyList,
            onViewClick = { emergency ->
                (activity as? AdminHome)?.stopSOSAlarm()
                (activity as? AdminHome)?.replaceFragment(EmergencyDetailFragment.newInstance(emergency))
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        listenForEmergencies()
    }

    private fun listenForEmergencies() {
        // Remove existing listener to prevent duplicate data streams
        emergencyListener?.remove()

        emergencyListener = db.collection("emergencies")
            .whereEqualTo("status", "active")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                emergencyList.clear()
                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots) {
                        val emergency = doc.toObject(Emergency::class.java).copy(id = doc.id)
                        emergencyList.add(emergency)
                    }
                    tvNoEmergencies.visibility = View.GONE
                } else {
                    tvNoEmergencies.visibility = View.VISIBLE
                    // Stop alarm if no active emergencies remain
                    (activity as? AdminHome)?.stopSOSAlarm()
                }
                adapter.updateList(emergencyList)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop listening when view is destroyed (e.g., navigating to details)
        emergencyListener?.remove()
    }
}
