import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.R

// 加载头像的扩展函数
fun ImageView.loadAvatar(url: String?) {
    // 添加日志
    Log.d("ImageUtils", "加载头像 URL: $url")
    
    // 修复 URL 中的双斜杠问题
    val fixedUrl = url?.replace("//api", "/api")
    Log.d("ImageUtils", "修复后的 URL: $fixedUrl")
    
    Glide.with(this.context)
        .load(fixedUrl)
        .apply(RequestOptions.circleCropTransform())
        .skipMemoryCache(true)  // 避免缓存问题
        .diskCacheStrategy(DiskCacheStrategy.NONE)  // 避免磁盘缓存问题
        .placeholder(R.drawable.default_avatar)
        .error(R.drawable.default_avatar)
        .into(this)
} 