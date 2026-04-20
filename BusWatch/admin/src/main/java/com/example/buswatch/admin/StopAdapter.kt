package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class StopAdmin(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val status: String = "active",
    var studentCount: Int = 0
)

class StopAdapter(
    private var stops: List<StopAdmin>,
    private val onViewClick: (StopAdmin) -> Unit,
    private val onEditClick: (StopAdmin) -> Unit,
    private val onArchiveClick: (StopAdmin) -> Unit
) : RecyclerView.Adapter<StopAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stopName: TextView = view.findViewById(R.id.tvStopName)
        val stopCoordinates: TextView = view.findViewById(R.id.tvStopCoordinates)
        val studentCount: TextView = view.findViewById(R.id.tvItemStopStudentCount)
        val btnView: ImageButton = view.findViewById(R.id.btnViewStop)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditStop)
        val btnArchive: ImageButton = view.findViewById(R.id.btnDeleteStop)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stop, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stop = stops[position]
        holder.stopName.text = stop.name
        holder.stopCoordinates.text = String.format("%.6f, %.6f", stop.latitude, stop.longitude)
        holder.studentCount.text = "${stop.studentCount} Students assigned"

        holder.btnView.setOnClickListener { onViewClick(stop) }
        holder.btnEdit.setOnClickListener { onEditClick(stop) }
        holder.btnArchive.setOnClickListener { onArchiveClick(stop) }
    }

    override fun getItemCount() = stops.size

    fun updateStops(newStops: List<StopAdmin>) {
        stops = newStops
        notifyDataSetChanged()
    }
}
