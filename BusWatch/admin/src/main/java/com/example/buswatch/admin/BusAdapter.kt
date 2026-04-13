package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class BusAdmin(
    val id: String,
    val busNumber: String,
    val status: String
)

class BusAdapter(
    private var buses: List<BusAdmin>,
    private val onViewClick: (BusAdmin) -> Unit,
    private val onEditClick: (BusAdmin) -> Unit,
    private val onArchiveClick: (BusAdmin) -> Unit
) : RecyclerView.Adapter<BusAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val busNumber: TextView = view.findViewById(R.id.tvBusNumber)
        val status: TextView = view.findViewById(R.id.tvBusStatus)
        val btnView: ImageButton = view.findViewById(R.id.btnView)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnArchive: ImageButton = view.findViewById(R.id.btnArchive)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bus, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bus = buses[position]
        holder.busNumber.text = bus.busNumber
        holder.status.text = bus.status

        holder.btnView.setOnClickListener { onViewClick(bus) }
        holder.btnEdit.setOnClickListener { onEditClick(bus) }
        holder.btnArchive.setOnClickListener { onArchiveClick(bus) }
    }

    override fun getItemCount() = buses.size
}
