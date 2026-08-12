package com.example.dspvoiceunvoice.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dspvoiceunvoice.R
import com.example.dspvoiceunvoice.databinding.ItemSegmentBinding
import com.example.dspvoiceunvoice.dsp.Segment

class SegmentAdapter(
    private val onSegmentClick: (Segment) -> Unit
) : RecyclerView.Adapter<SegmentAdapter.SegmentViewHolder>() {

    private var segments: List<Segment> = emptyList()

    fun submitList(newList: List<Segment>) {
        this.segments = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SegmentViewHolder {
        val binding = ItemSegmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SegmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SegmentViewHolder, position: Int) {
        holder.bind(segments[position])
    }

    override fun getItemCount(): Int = segments.size

    inner class SegmentViewHolder(
        private val binding: ItemSegmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(segment: Segment) {
            binding.tvSegmentTitle.text = "#${segment.index}. ${segment.labelName}"
            binding.tvSegmentTime.text = String.format(
                "%.2fs - %.2fs (Thời lượng: %.2fs)",
                segment.startTime,
                segment.endTime,
                segment.duration
            )

            val badgeRes = when (segment.label) {
                2 -> R.drawable.shape_badge_voice
                1 -> R.drawable.shape_badge_unvoice
                else -> R.drawable.shape_badge_silence
            }
            binding.viewLabelBadge.setBackgroundResource(badgeRes)

            val textColor = when (segment.label) {
                2 -> Color.parseColor("#4ADE80")
                1 -> Color.parseColor("#FBBF24")
                else -> Color.parseColor("#94A3B8")
            }
            binding.tvSegmentTitle.setTextColor(textColor)

            binding.btnPlaySegment.setOnClickListener {
                onSegmentClick(segment)
            }
            binding.root.setOnClickListener {
                onSegmentClick(segment)
            }
        }
    }
}
