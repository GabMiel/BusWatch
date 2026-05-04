package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR

class PendingUserAdapter(
    private var users: MutableList<UserAdmin>,
    private val onViewClick: (UserAdmin) -> Unit
) : RecyclerView.Adapter<PendingUserAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvUserName)
        val status: TextView = view.findViewById(R.id.tvUserStatus)
        val avatar: ImageView = view.findViewById(R.id.imgAvatar)
        val btnView: ImageButton = view.findViewById(R.id.btnView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_pending, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.name.text = user.name
        holder.status.setText(CommonR.string.pending_registration)

        Glide.with(holder.itemView.context)
            .load(user.avatarUrl)
            .placeholder(CommonR.drawable.ic_person_placeholder)
            .circleCrop()
            .into(holder.avatar)

        holder.btnView.setOnClickListener { onViewClick(user) }
        holder.itemView.setOnClickListener { onViewClick(user) }
    }

    override fun getItemCount() = users.size
}
