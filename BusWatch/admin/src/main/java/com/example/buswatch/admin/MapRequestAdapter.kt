package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MapRequestAdapter(
    private var requests: MutableList<MapRequest>,
    private val onViewClick: (MapRequest, Int) -> Unit
) : RecyclerView.Adapter<MapRequestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val studentName: TextView = view.findViewById(R.id.tvStudentName)
        val btnView: ImageButton = view.findViewById(R.id.btnView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_map_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        holder.studentName.text = request.studentName

        holder.btnView.setOnClickListener { onViewClick(request, position) }
        holder.itemView.setOnClickListener { onViewClick(request, position) }
    }

    override fun getItemCount() = requests.size
}