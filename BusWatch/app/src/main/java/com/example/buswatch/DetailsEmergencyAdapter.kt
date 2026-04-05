package com.example.buswatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class EmergencyContact(
    val name: String,
    val relation: String,
    val phone: String,
    val email: String,
    val isPrimary: Boolean = false
)

class DetailsEmergencyAdapter(
    private val contacts: List<EmergencyContact>
) : RecyclerView.Adapter<DetailsEmergencyAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvContactName)
        val relation: TextView = view.findViewById(R.id.tvContactRelation)
        val phone: TextView = view.findViewById(R.id.tvContactPhone)
        val email: TextView = view.findViewById(R.id.tvContactEmail)
        val primaryBadge: View = view.findViewById(R.id.tvPrimaryBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_details_emergency, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.name.text = if (contact.name.isEmpty()) "---" else contact.name
        holder.relation.text = if (contact.relation.isEmpty()) "---" else contact.relation
        holder.phone.text = if (contact.phone.isEmpty()) "---" else contact.phone
        holder.email.text = if (contact.email.isEmpty()) "---" else contact.email
        
        holder.primaryBadge.visibility = if (contact.isPrimary) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = contacts.size
}
