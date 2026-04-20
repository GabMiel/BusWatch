package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.R
import com.google.firebase.firestore.FirebaseFirestore

class DashboardFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStats(view)
    }

    private fun loadStats(view: View) {
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
    }
}
