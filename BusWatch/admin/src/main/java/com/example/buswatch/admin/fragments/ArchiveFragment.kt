package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.*
import com.google.firebase.firestore.FirebaseFirestore

class ArchiveFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var currentTab = AdminHome.ArchiveTab.PARENTS

    companion object {
        fun newInstance(tab: AdminHome.ArchiveTab) = ArchiveFragment().apply {
            arguments = Bundle().apply {
                putSerializable("tab", tab)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tab = arguments?.getSerializable("tab") as? AdminHome.ArchiveTab
        if (tab != null) currentTab = tab
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layoutRes = when (currentTab) {
            AdminHome.ArchiveTab.PARENTS -> R.layout.fragment_archive
            AdminHome.ArchiveTab.DRIVERS -> R.layout.fragment_driver_archive
            AdminHome.ArchiveTab.CONDUCTORS -> R.layout.fragment_conductor_archive
            AdminHome.ArchiveTab.BUS -> R.layout.fragment_bus_archive
            AdminHome.ArchiveTab.STOPS -> R.layout.fragment_stop_archive
            AdminHome.ArchiveTab.ROUTES -> R.layout.fragment_route_archive
        }
        return inflater.inflate(layoutRes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchData()
    }

    private fun setupUI(view: View) {
        view.findViewById<TextView>(R.id.tabArchivedParents)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.PARENTS) }
        view.findViewById<TextView>(R.id.tabArchivedDrivers)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.DRIVERS) }
        view.findViewById<TextView>(R.id.tabArchivedConductors)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.CONDUCTORS) }
        view.findViewById<TextView>(R.id.tabArchivedBus)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.BUS) }
        view.findViewById<TextView>(R.id.tabArchivedStops)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.STOPS) }
        view.findViewById<TextView>(R.id.tabArchivedRoutes)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.ROUTES) }

        view.findViewById<RecyclerView>(R.id.recyclerArchived)?.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun switchTab(tab: AdminHome.ArchiveTab) {
        (requireActivity() as? AdminHome)?.replaceFragment(newInstance(tab))
    }

    private fun fetchData() {
        when (currentTab) {
            AdminHome.ArchiveTab.PARENTS -> fetchParents()
            AdminHome.ArchiveTab.DRIVERS -> fetchDrivers()
            AdminHome.ArchiveTab.CONDUCTORS -> fetchConductors()
            AdminHome.ArchiveTab.BUS -> fetchBuses()
            AdminHome.ArchiveTab.STOPS -> fetchStops()
            AdminHome.ArchiveTab.ROUTES -> fetchRoutes()
        }
    }

    private fun fetchParents() {
        db.collection("parents").whereIn("status", listOf("archived", "rejected")).get().addOnSuccessListener { documents ->
            val list = documents.map { doc ->
                @Suppress("UNCHECKED_CAST")
                val profile = doc.get("profile") as? Map<String, Any>
                UserAdmin(doc.id, "${profile?.get("firstName")} ${profile?.get("lastName")}", "Parent", isArchived = true, status = doc.getString("status") ?: "archived")
            }
            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedUserAdapter(list,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreUserInternal(it) },
                onDeleteClick = { (requireActivity() as? AdminHome)?.deleteUserInternal(it) },
                onViewClick = { (requireActivity() as? AdminHome)?.showParentDetail(it) { fetchData() } }
            )
        }
    }

    private fun fetchDrivers() {
        db.collection("drivers").whereEqualTo("status", "archived").get().addOnSuccessListener { documents ->
            val list = documents.map { doc -> UserAdmin(doc.id, "${doc.getString("firstName")} ${doc.getString("lastName")}", "Driver", isArchived = true, status = "archived") }
            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedUserAdapter(list,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreDriverInternal(it) },
                onDeleteClick = { (requireActivity() as? AdminHome)?.deleteUserInternal(it) },
                onViewClick = { (requireActivity() as? AdminHome)?.showDriverDetail(it) { fetchData() } }
            )
        }
    }

    private fun fetchConductors() {
        db.collection("conductors").whereEqualTo("status", "archived").get().addOnSuccessListener { documents ->
            val list = documents.map { doc -> UserAdmin(doc.id, "${doc.getString("firstName")} ${doc.getString("lastName")}", "Conductor", isArchived = true, status = "archived") }
            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedUserAdapter(list,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreConductorInternal(it) },
                onDeleteClick = { (requireActivity() as? AdminHome)?.deleteUserInternal(it) },
                onViewClick = { (requireActivity() as? AdminHome)?.showConductorDetail(it) { fetchData() } }
            )
        }
    }

    private fun fetchBuses() {
        db.collection("buses").whereEqualTo("status", "Archived").get().addOnSuccessListener { snapshots ->
            val list = snapshots.map { doc -> BusAdmin(doc.id, doc.getString("busNumber") ?: "N/A", "Archived") }
            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedBusAdapter(list,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreBusInternal(it) },
                onDeleteClick = { (requireActivity() as? AdminHome)?.deleteBusInternal(it) },
                onViewClick = { (requireActivity() as? AdminHome)?.showBusDetail(it) }
            )
        }
    }

    private fun fetchStops() {
        db.collection("stops").whereEqualTo("status", "archived").get().addOnSuccessListener { snapshots ->
            val list = snapshots.map { doc -> StopAdmin(doc.id, doc.getString("name") ?: "N/A", doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0) }
            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedStopAdapter(list,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreStopInternal(it) },
                onDeleteClick = { (requireActivity() as? AdminHome)?.deleteStopInternal(it) },
                onViewClick = { (requireActivity() as? AdminHome)?.showStopDetailInternal(it) { switchTab(AdminHome.ArchiveTab.STOPS) } }
            )
        }
    }

    private fun fetchRoutes() {
        db.collection("routes").whereEqualTo("status", "Archived").get().addOnSuccessListener { snapshots ->
            val list = snapshots.map { doc -> RouteAdmin(doc.id, doc.getString("routeName") ?: "N/A", doc.getString("busNumber") ?: "N/A", doc.getString("driverName") ?: "N/A", doc.getLong("currentCapacity")?.toInt() ?: 0, doc.getLong("maxCapacity")?.toInt() ?: 0, "Archived") }
            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedRouteAdapter(list,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreRouteInternal(it) },
                onDeleteClick = { (requireActivity() as? AdminHome)?.deleteRouteInternal(it) },
                onViewClick = { (requireActivity() as? AdminHome)?.showRouteDetailInternal(it) { switchTab(AdminHome.ArchiveTab.ROUTES) } }
            )
        }
    }
}
