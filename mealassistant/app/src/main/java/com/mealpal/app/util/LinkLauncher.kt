package com.mealpal.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object LinkLauncher {

    /** Opens a store deep link (imeituan://, tbopen://, https://…). Falls back to browser. */
    fun launch(context: Context, rawLink: String) {
        val link = rawLink.trim()
        if (link.isEmpty()) {
            toast(context, "这个店铺还没设置链接")
            return
        }
        if (tryView(context, link)) return

        // Deep link failed: retry as web URL if it looks like a bare domain/path
        if (!link.startsWith("http", ignoreCase = true) &&
            !link.contains("://")
        ) {
            if (tryView(context, "https://$link")) return
        }
        toast(context, "无法打开链接，请检查是否安装了对应 App")
    }

    private fun tryView(context: Context, url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
