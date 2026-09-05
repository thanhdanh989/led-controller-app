package com.danh.ledcontroller

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Danh sach mau (toi da 8) dung cho cac che do: Xep gach, Quet gradient, Chuyen mau theo bang.
 * Moi mau la IntArray[3] = {r, g, b}.
 */
class PaletteAdapter(
    private val colors: MutableList<IntArray>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<PaletteAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val swatch: View = view.findViewById(R.id.swatch)
        val remove: View = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_color_swatch, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = colors[position]
        holder.swatch.setBackgroundColor(Color.rgb(c[0], c[1], c[2]))
        holder.remove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (colors.size <= 1) return@setOnClickListener // luon giu it nhat 1 mau
            colors.removeAt(pos)
            notifyItemRemoved(pos)
            onChanged()
        }
    }

    override fun getItemCount(): Int = colors.size

    fun addColor(rgb: IntArray) {
        if (colors.size >= 8) return
        colors.add(rgb)
        notifyItemInserted(colors.size - 1)
        onChanged()
    }
}
