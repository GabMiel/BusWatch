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
import com.example.buswatch.admin.R
import com.example.buswatch.admin.RouteAdmin
import com.example.buswatch.admin.RouteAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class RoutingFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var sortDirection = Query.Direction.ASCENDING

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_routing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchPage()
    }

    private fun setupUI(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recyclerRoutes)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<TextView>(R.id.tabAllRoutesMap)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadRouteMap()
        }
        
        view.findViewById<TextView>(R.id.tabArchivedRoutesHeader)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadArchive(AdminHome.ArchiveTab.ROUTES)
        }

        view.findViewById<TextView>(R.id.btnAddNewRoute)?.setOnClickListener { 
             (requireActivity() as? AdminHome)?.showAddNewRouteDialogInternal()
        }
    }

    private fun fetchPage() {
        db.collection("routes")
            .whereEqualTo("status", "Active")
            .orderBy("routeName", sortDirection)
            .get()
            .addOnSuccessListener { snapshots ->
                val routes = snapshots.map { doc -> 
                    RouteAdmin(doc.id, doc.getString("routeName") ?: "N/A", 
                        doc.getString("busNumber") ?: "N/A", doc.getString("driverName") ?: "N/A", 
                        doc.getLong("currentCapacity")?.toInt() ?: 0, 
                        doc.getLong("maxCapacity")?.toInt() ?: 0, 
                        doc.getString("status") ?: "Active") 
                }
                view?.findViewById<RecyclerView>(R.id.recyclerRoutes)?.adapter = RouteAdapter(routes, 
                    onViewClick = { (requireActivity() as? AdminHome)?.showRouteDetailInternal(it) }, 
                    onEditClick = { (requireActivity() as? AdminHome)?.editRouteDetailInternal(it) }, 
                    onArchiveClick = { (requireActivity() as? AdminHome)?.archiveRouteInternal(it) })
            }
    }
}
