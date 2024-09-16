package com.example.finalprojectneshan


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.neshan.common.model.LatLng
import org.neshan.servicessdk.search.model.Item

class SearchAdapter(
    private var items: List<Item>,
    private val onSearchItemListener: OnSearchItemListener,
) : RecyclerView.Adapter<SearchAdapter.ViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.item_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
        holder.bind2(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun updateList(items: List<Item>) {
        this.items = items
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.textView_title)
        val tvAddress: TextView = itemView.findViewById(R.id.textView_address)

        init {
            itemView.setOnClickListener {
                val location = items[adapterPosition].location
                val LatLng = LatLng(location.latitude, location.longitude)
                onSearchItemListener.onSearchItemClick(items[adapterPosition])

            }

        }

        fun bind(item: Item) {
            tvTitle.text = item.title
            tvAddress.text = item.address
        }
        fun bind2(data:Item){
            tvTitle.text=data.address
            tvAddress.text=data.address
        }

    }

    interface OnSearchItemListener {
        fun onSearchItemClick(LatLng: Item?)
    }

}