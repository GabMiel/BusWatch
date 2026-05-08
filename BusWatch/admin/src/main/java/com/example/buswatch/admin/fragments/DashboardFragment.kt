package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DashboardFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var emergencyListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<View>(R.id.cardEmergencies)?.setOnClickListener {
            (activity as? AdminHome)?.replaceFragment(EmergenciesFragment())
        }
        
        view.findViewById<View>(R.id.cardPending)?.setOnClickListener {
            (activity as? AdminHome)?.replaceFragment(ApprovalsFragment.newInstance(ApprovalsFragment.Tab.REGISTRATION))
        }

        view.findViewById<View>(R.id.cardPendingMaps)?.setOnClickListener {
            (activity as? AdminHome)?.replaceFragment(ApprovalsFragment.newInstance(ApprovalsFragment.Tab.MAP))
        }

        view.findViewById<View>(R.id.cardPendingStops)?.setOnClickListener {
            (activity as? AdminHome)?.replaceFragment(ApprovalsFragment.newInstance(ApprovalsFragment.Tab.STOPS))
        }
        
        loadStats(view)
        startSOSListener()
    }

    private fun startSOSListener() {
        emergencyListener = db.collection("emergencies")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                
                if (snapshots != null) {
                    var hasNewEmergency = false
                    for (dc in snapshots.documentChanges) {
                        if (dc.type == DocumentChange.Type.ADDED) {
                            hasNewEmergency = true
                        }
                    }
                    
                    if (hasNewEmergency && !snapshots.isEmpty) {
                        (activity as? AdminHome)?.playSOSAlarm()
                    } else if (snapshots.isEmpty) {
                        (activity as? AdminHome)?.stopSOSAlarm()
                    }
                }
            }
    }

    private fun loadStats(view: View) {
        // Active SOS Alerts
        db.collection("emergencies").whereEqualTo("status", "active").addSnapshotListener { s, _ ->
            view.findViewById<TextView>(R.id.tvCountEmergencies)?.text = (s?.size() ?: 0).toString()
        }

        // Total Students
        db.collectionGroup("students").get().addOnSuccessListener { s ->
            var total = s.size()
            // Also count 'child' and 'children' fields in parents documents
            db.collection("parents").get().addOnSuccessListener { parents ->
                for (doc in parents) {
                    if (doc.contains("child")) total++
                    @Suppress("UNCHECKED_CAST")
                    val additional = doc.get("children") as? List<*>
                    total += additional?.size ?: 0
                }
                view.findViewById<TextView>(R.id.tvCountStudents)?.text = total.toString()
            }
        }

        // Total Parents (Approved)
        db.collection("parents").whereEqualTo("status", "approved").get().addOnSuccessListener { s -> 
            view.findViewById<TextView>(R.id.tvCountParents)?.text = s.size().toString() 
        }
        
        // Total Drivers (Active)
        db.collection("drivers").whereEqualTo("status", "active").get().addOnSuccessListener { s -> 
            view.findViewById<TextView>(R.id.tvCountDrivers)?.text = s.size().toString() 
        }

        // Total Conductors (Active)
        db.collection("conductors").whereEqualTo("status", "active").get().addOnSuccessListener { s -> 
            view.findViewById<TextView>(R.id.tvCountConductors)?.text = s.size().toString() 
        }
        
        // Total Buses (Active)
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { s -> 
            view.findViewById<TextView>(R.id.tvCountBuses)?.text = s.size().toString() 
        }

        // Total Stops (Active)
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { s -> 
            view.findViewById<TextView>(R.id.tvCountStops)?.text = s.size().toString() 
        }

        // Total Routes (Active)
        db.collection("routes").whereEqualTo("status", "Active").get().addOnSuccessListener { s -> 
            view.findViewById<TextView>(R.id.tvCountRoutes)?.text = s.size().toString() 
        }
        
        // Pending Parents
        db.collection("parents").whereEqualTo("status", "pending").get().addOnSuccessListener { s -> 
            view.findViewById<TextView>(R.id.tvCountPending)?.text = s.size().toString() 
        }

        // Pending Maps
        db.collection("map_requests").whereEqualTo("status", "pending").addSnapshotListener { s, _ ->
            view.findViewById<TextView>(R.id.tvCountPendingMaps)?.text = (s?.size() ?: 0).toString()
        }

        // Pending Stops
        db.collection("stop_requests").whereEqualTo("status", "pending").addSnapshotListener { s, _ ->
            view.findViewById<TextView>(R.id.tvCountPendingStops)?.text = (s?.size() ?: 0).toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        emergencyListener?.remove()
    }
}
