package com.trackigniter.client

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trackigniter.client.api.models.Expense
import com.trackigniter.client.api.models.Fuel

class InfoEntryAdapter(
    private val onDelete: (String, Boolean) -> Unit, // id, isFuel
    private val onReceiptClick: ((String) -> Unit)? = null // receiptUrl
) : RecyclerView.Adapter<InfoEntryAdapter.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private var isFuelList = false

    fun submitExpenses(expenses: List<Expense>) {
        isFuelList = false
        items.clear()
        items.addAll(expenses)
        notifyDataSetChanged()
    }

    fun submitFuel(fuel: List<Fuel>) {
        isFuelList = true
        items.clear()
        items.addAll(fuel)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_info_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        if (isFuelList && item is Fuel) {
            holder.bindFuel(item)
        } else if (item is Expense) {
            holder.bindExpense(item)
        }
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.entryTitle)
        private val subtitle: TextView = view.findViewById(R.id.entrySubtitle)
        private val amount: TextView = view.findViewById(R.id.entryAmount)
        private val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
        private val btnReceipt: ImageView? = view.findViewById(R.id.btnReceipt)

        fun bindExpense(expense: Expense) {
            title.text = expense.title ?: "Expense"
            subtitle.text = expense.date ?: ""
            amount.text = "${expense.amount ?: "0"}"
            btnReceipt?.visibility = View.GONE

            btnDelete.setOnClickListener {
                onDelete(expense.id, false)
            }
        }

        fun bindFuel(fuel: Fuel) {
            title.text = "${fuel.quantity}" // Removed Ltrs
            subtitle.text = fuel.date ?: ""
            amount.text = "${fuel.price ?: "0"}"

            if (!fuel.image.isNullOrEmpty()) {
                btnReceipt?.visibility = View.VISIBLE
                btnReceipt?.setOnClickListener {
                    onReceiptClick?.invoke(fuel.image)
                }
            } else {
                btnReceipt?.visibility = View.GONE
            }

            btnDelete.setOnClickListener {
                onDelete(fuel.id, true)
            }
        }
    }
}
