package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AddBusDialog
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.BusAdmin
import com.example.buswatch.admin.BusAdapter
import com.example.buswatch.admin.R
import com.google.firebase.firestore.FirebaseFirestore

class BusesFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bus, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchBuses()
    }

    private fun setupUI(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recyclerBuses)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<TextView>(R.id.btnAddNewBus)?.setOnClickListener {
            AddBusDialog(requireActivity(), db) { fetchBuses() }.show()
        }
    }

    private fun fetchBuses() {
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            val busList = snapshots.map { BusAdmin(it.id, it.getString("busNumber") ?: "N/A", it.getString("status") ?: "Active") }
            view?.findViewById<RecyclerView>(R.id.recyclerBuses)?.adapter = BusAdapter(busList,
                onViewClick = { (requireActivity() as? AdminHome)?.showBusDetail(it) },
                onEditClick = { (requireActivity() as? AdminHome)?.editBusDetail(it) },
                onArchiveClick = { (requireActivity() as? AdminHome)?.archiveBus(it) }
            )
        }
    }
}
