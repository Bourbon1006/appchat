import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appchat.R
import com.example.appchat.model.MomentComment
import java.time.format.DateTimeFormatter
import com.example.appchat.api.ApiClient

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {
    private var comments = listOf<MomentComment>()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivUserAvatar: ImageView = itemView.findViewById(R.id.ivUserAvatar)
        val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = comments[position]
        
        // 加载头像
        Glide.with(holder.itemView.context)
            .load(comment.userAvatar)
            .placeholder(R.drawable.default_avatar)
            .into(holder.ivUserAvatar)

        holder.tvUsername.text = comment.username
        holder.tvContent.text = comment.content
        holder.tvTime.text = comment.createTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }

    override fun getItemCount() = comments.size

    fun updateComments(newComments: List<MomentComment>) {
        comments = newComments
        notifyDataSetChanged()
    }
} 