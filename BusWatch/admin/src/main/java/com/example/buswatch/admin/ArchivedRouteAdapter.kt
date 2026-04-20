package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ArchivedRouteAdapter(
    private val routes: List<RouteAdmin>,
    private val onRestoreClick: (RouteAdmin) -> Unit,
    private val onDeleteClick: (RouteAdmin) -> Unit,
    private val onViewClick: (RouteAdmin) -> Unit
) : RecyclerView.Adapter<ArchivedRouteAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val routeName: TextView = view.findViewById(R.id.tvRouteName)
        val status: TextView = view.findViewById(R.id.tvRouteStatus)
        val btnRestore: ImageButton = view.findViewById(R.id.btnRestore)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_route_archive, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val route = routes[position]
        holder.routeName.text = route.routeName
        holder.status.text = "Archived"

        holder.btnRestore.setOnClickListener { onRestoreClick(route) }
        holder.btnDelete.setOnClickListener { onDeleteClick(route) }
        holder.itemView.setOnClickListener { onViewClick(route) }
    }

    override fun getItemCount() = routes.size
}
