package com.omega.attendance.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.omega.attendance.BuildConfig
import com.omega.attendance.R

@Composable
fun AboutSection() {
    val context = LocalContext.current

    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // The contact links are optional; the offline attendance app remains usable.
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("حول البرنامج", style = MaterialTheme.typography.titleLarge)
            Image(
                painter = painterResource(R.drawable.developer_portrait),
                contentDescription = "صورة المبرمج مصطفى تركي",
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .widthIn(max = 220.dp)
                    .aspectRatio(612f / 1280f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
            Text("المبرمج: مصطفى تركي")
            Text("واتساب: 07705658419")
            Text("الإصدار: ${BuildConfig.VERSION_NAME}")
            OutlinedButton(
                onClick = { openLink("https://wa.me/9647705658419") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("التواصل عبر واتساب") }
            OutlinedButton(
                onClick = { openLink("https://www.facebook.com/mustafa.almaliki") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("صفحة فيسبوك") }
            OutlinedButton(
                onClick = { openLink("https://www.instagram.com/mustafa.turki.almaliki/") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("حساب إنستغرام") }
        }
    }
}
