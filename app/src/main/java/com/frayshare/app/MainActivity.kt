package com.frayshare.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.frayshare.app.core.share.ShareIn
import com.frayshare.app.core.share.ShareInbox
import com.frayshare.app.ui.FrayApp
import com.frayshare.app.ui.theme.FrayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // При пересоздании активити интент тот же — не писать share-файлы заново.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FrayTheme {
                FrayApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val items = runCatching { ShareIn.collect(this, intent) }.getOrDefault(emptyList())
        if (items.isNotEmpty()) ShareInbox.push(items)
    }
}
