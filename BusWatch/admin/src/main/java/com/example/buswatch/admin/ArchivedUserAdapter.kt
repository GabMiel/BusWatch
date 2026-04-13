package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ArchivedUserAdapter(
    private val users: List<UserAdmin>,
    private val onRestoreClick: (UserAdmin) -> Unit,
    private val onDeleteClick: (UserAdmin) -> Unit,
    private val onViewClick: (UserAdmin) -> Unit
) : RecyclerView.Adapter<ArchivedUserAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.imgAvatar)
        val name: TextView = view.findViewById(R.id.tvUserName)
        val btnRestore: ImageButton = view.findViewById(R.id.btnRestore)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_archived, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.name.text = user.name
        
        // Use a generic placeholder or actual avatar if available in UserAdmin (currently it's not, but AdminHome fetches it)
        // For now, just set placeholder. 
        // Note: UserAdmin data class doesn't have avatarUrl. 
        // In a real app, I'd add it to UserAdmin.
        
        holder.btnRestore.setOnClickListener { onRestoreClick(user) }
        holder.btnDelete.setOnClickListener { onDeleteClick(user) }
        holder.itemView.setOnClickListener { onViewClick(user) }
    }

    override fun getItemCount() = users.size
}
