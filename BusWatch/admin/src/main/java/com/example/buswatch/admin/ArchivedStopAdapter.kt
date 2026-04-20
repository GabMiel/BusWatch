package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ArchivedStopAdapter(
    private val stops: List<StopAdmin>,
    private val onRestoreClick: (StopAdmin) -> Unit,
    private val onDeleteClick: (StopAdmin) -> Unit,
    private val onViewClick: (StopAdmin) -> Unit
) : RecyclerView.Adapter<ArchivedStopAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stopName: TextView = view.findViewById(R.id.tvStopName)
        val status: TextView = view.findViewById(R.id.tvStopStatus)
        val btnRestore: ImageButton = view.findViewById(R.id.btnRestore)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stop_archive, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stop = stops[position]
        holder.stopName.text = stop.name
        holder.status.text = "Archived"

        holder.btnRestore.setOnClickListener { onRestoreClick(stop) }
        holder.btnDelete.setOnClickListener { onDeleteClick(stop) }
        holder.itemView.setOnClickListener { onViewClick(stop) }
    }

    override fun getItemCount() = stops.size
}
