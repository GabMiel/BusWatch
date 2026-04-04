package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PendingUserAdapter(
    private var users: MutableList<UserAdmin>,
    private val onAcceptClick: (UserAdmin, Int) -> Unit,
    private val onRejectClick: (UserAdmin, Int) -> Unit,
    private val onViewClick: (UserAdmin) -> Unit
) : RecyclerView.Adapter<PendingUserAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvUserName)
        val status: TextView = view.findViewById(R.id.tvUserStatus)
        val btnAccept: ImageButton = view.findViewById(R.id.btnAccept)
        val btnReject: ImageButton = view.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_pending, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.name.text = user.name
        holder.status.text = "Pending"

        holder.btnAccept.setOnClickListener { onAcceptClick(user, position) }
        holder.btnReject.setOnClickListener { onRejectClick(user, position) }
        holder.itemView.setOnClickListener { onViewClick(user) }
    }

    override fun getItemCount() = users.size

    fun removeAt(position: Int) {
        users.removeAt(position)
        notifyItemRemoved(position)
    }
}
