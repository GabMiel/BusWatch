package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DriverAdapter(
    private var drivers: MutableList<UserAdmin>,
    private val onViewClick: (UserAdmin) -> Unit,
    private val onEditClick: (UserAdmin) -> Unit,
    private val onArchiveClick: (UserAdmin, Int) -> Unit
) : RecyclerView.Adapter<DriverAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvUserName)
        val role: TextView = view.findViewById(R.id.tvUserRole)
        val btnView: ImageButton = view.findViewById(R.id.btnView)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnArchive: ImageButton = view.findViewById(R.id.btnArchive)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_driver, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val driver = drivers[position]
        holder.name.text = driver.name
        holder.role.text = driver.role

        holder.btnView.setOnClickListener { onViewClick(driver) }
        holder.btnEdit.setOnClickListener { onEditClick(driver) }
        holder.btnArchive.setOnClickListener { onArchiveClick(driver, position) }
    }

    override fun getItemCount() = drivers.size
}
