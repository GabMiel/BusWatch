package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

class EmergencyAdapter(
    private var emergencies: List<Emergency>,
    private val onViewClick: (Emergency) -> Unit
) : RecyclerView.Adapter<EmergencyAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEmergencyType: TextView = view.findViewById(R.id.tvEmergencyType)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvDriverName: TextView = view.findViewById(R.id.tvDriverName)
        val tvRouteBusInfo: TextView = view.findViewById(R.id.tvRouteBusInfo)
        val btnView: MaterialButton = view.findViewById(R.id.btnView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emergency, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val emergency = emergencies[position]
        holder.tvEmergencyType.text = emergency.type
        
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        holder.tvTimestamp.text = emergency.timestamp?.toDate()?.let { sdf.format(it) } ?: "Just now"
        
        holder.tvDriverName.text = "Driver: ${emergency.driverName}"
        holder.tvRouteBusInfo.text = "${emergency.routeName ?: "No Route"} • ${emergency.busNumber ?: "No Bus"}"
        
        holder.btnView.setOnClickListener { onViewClick(emergency) }
    }

    override fun getItemCount() = emergencies.size

    fun updateList(newList: List<Emergency>) {
        emergencies = newList
        notifyDataSetChanged()
    }
}
