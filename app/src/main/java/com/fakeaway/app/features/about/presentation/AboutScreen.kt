package com.fakeaway.app.features.about.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fakeaway.app.R
import com.fakeaway.app.core.ui.components.SectionHeader
import com.fakeaway.app.core.ui.theme.ForensicCyan
import com.fakeaway.app.core.ui.theme.ForensicReal
import com.fakeaway.app.core.ui.theme.MidnightBg
import com.fakeaway.app.core.ui.theme.MidnightBorder
import com.fakeaway.app.core.ui.theme.MidnightSurface
import com.fakeaway.app.core.ui.theme.MidnightSurfaceElevated
import com.fakeaway.app.core.ui.theme.TextPrimary
import com.fakeaway.app.core.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openUri(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MidnightBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("about_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = ForensicCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Blue Team Hero Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                border = BorderStroke(1.5.dp, ForensicCyan.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ForensicCyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = ForensicCyan,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.about_team_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = ForensicCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ForensicReal.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ForensicReal.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = stringResource(R.string.about_created_in),
                            style = MaterialTheme.typography.labelMedium,
                            color = ForensicReal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Mosul Space Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUri("https://mosulspace.org/") }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Hub,
                        contentDescription = null,
                        tint = ForensicCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.about_hub_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "https://mosulspace.org/",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForensicCyan
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.about_hub_visit),
                        tint = ForensicCyan
                    )
                }
            }

            // Lead Developer & Software/Design Contact Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurfaceElevated),
                border = BorderStroke(1.dp, ForensicReal.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = ForensicReal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "عدنان سلطان حسن",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.about_lead_role),
                                style = MaterialTheme.typography.bodySmall,
                                color = ForensicCyan
                            )
                        }
                    }

                    Text(
                        text = "07740851231",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { openUri("https://wa.me/9647740851231") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForensicReal,
                                contentColor = MidnightBg
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.about_whatsapp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { openUri("tel:07740851231") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.about_call))
                        }
                    }
                }
            }

            // Team Members List
            SectionHeader(title = stringResource(R.string.about_members_title))
            val members = listOf(
                "ايه اياد ثامر",
                "سدرة احمد محمد شيت",
                "خالد محمد علي"
            )
            members.forEach { memberName ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, MidnightBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = ForensicCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = memberName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
