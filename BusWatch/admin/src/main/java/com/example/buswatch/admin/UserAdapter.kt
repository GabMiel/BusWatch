package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class UserAdmin(
    val id: String,
    val name: String,
    val role: String,
    var isArchived: Boolean = false,
    var status: String = "active"
)

class UserAdapter(
    private var users: MutableList<UserAdmin>,
    private val onViewClick: (UserAdmin) -> Unit,
    private val onArchiveClick: (UserAdmin, Int) -> Unit
) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvUserName)
        val role: TextView = view.findViewById(R.id.tvUserRole)
        val btnView: ImageButton = view.findViewById(R.id.btnView)
        val btnArchive: ImageButton = view.findViewById(R.id.btnArchive)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.name.text = user.name
        holder.role.text = user.role

        holder.btnView.setOnClickListener { onViewClick(user) }
        holder.btnArchive.setOnClickListener { onArchiveClick(user, position) }
    }

    override fun getItemCount() = users.size
}