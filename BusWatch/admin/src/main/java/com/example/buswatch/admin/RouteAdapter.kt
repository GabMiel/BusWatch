package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR

class RouteAdapter(
    private val routes: List<RouteAdmin>,
    private val onViewClick: (RouteAdmin) -> Unit,
    private val onEditClick: (RouteAdmin) -> Unit,
    private val onArchiveClick: (RouteAdmin) -> Unit
) : RecyclerView.Adapter<RouteAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRouteName: TextView = view.findViewById(R.id.tvRouteName)
        val tvBusInfo: TextView = view.findViewById(R.id.tvBusInfo)
        val tvDriverInfo: TextView = view.findViewById(R.id.tvDriverInfo)
        val tvCapacityInfo: TextView = view.findViewById(R.id.tvCapacityInfo)
        val btnView: ImageButton = view.findViewById(R.id.btnView)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnArchive: ImageButton = view.findViewById(R.id.btnArchive)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_route, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val route = routes[position]
        val context = holder.itemView.context
        holder.tvRouteName.text = route.routeName
        holder.tvBusInfo.text = context.getString(CommonR.string.bus_format, route.busNumber)
        holder.tvDriverInfo.text = context.getString(CommonR.string.driver_format, route.driverName)
        holder.tvCapacityInfo.text = context.getString(CommonR.string.capacity_format, route.currentCapacity, route.maxCapacity)

        holder.btnView.setOnClickListener { onViewClick(route) }
        holder.btnEdit.setOnClickListener { onEditClick(route) }
        holder.btnArchive.setOnClickListener { onArchiveClick(route) }
    }

    override fun getItemCount() = routes.size
}
