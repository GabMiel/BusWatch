package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ArchivedBusAdapter(
    private val buses: List<BusAdmin>,
    private val onRestoreClick: (BusAdmin) -> Unit,
    private val onDeleteClick: (BusAdmin) -> Unit,
    private val onViewClick: (BusAdmin) -> Unit
) : RecyclerView.Adapter<ArchivedBusAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val busNumber: TextView = view.findViewById(R.id.tvBusNumber)
        val status: TextView = view.findViewById(R.id.tvBusStatus)
        val btnRestore: ImageButton = view.findViewById(R.id.btnRestore)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bus_archive, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bus = buses[position]
        holder.busNumber.text = bus.busNumber
        holder.status.text = bus.status

        holder.btnRestore.setOnClickListener { onRestoreClick(bus) }
        holder.btnDelete.setOnClickListener { onDeleteClick(bus) }
        holder.itemView.setOnClickListener { onViewClick(bus) }
    }

    override fun getItemCount() = buses.size
}
