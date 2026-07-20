package com.foundation.scpreader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundation.scpreader.AppState
import com.foundation.scpreader.network.FriendsApi
import com.foundation.scpreader.ui.components.AppIcons
import com.foundation.scpreader.ui.components.Divider1
import com.foundation.scpreader.ui.components.Mono
import com.foundation.scpreader.ui.theme.LocalScpScheme

@Composable
fun FriendsScreen(app: AppState) {
    val c = LocalScpScheme.current
    val ctx = LocalContext.current
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(bottom = 108.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Friends", fontSize = 28.sp, color = c.onSurface, modifier = Modifier.weight(1f))
            if (app.friendsLoading) CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.dp)
        }

        // ---- your code ----
        GroupLabel2("Your friend code")
        Card2 {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    app.friendCode ?: "······",
                    fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp, color = c.primary, modifier = Modifier.weight(1f),
                )
                val code = app.friendCode
                CircleIcon(AppIcons.ContentCopy, enabled = code != null) { copyToClipboard(ctx, code!!) }
                CircleIcon(AppIcons.Share, enabled = code != null) { shareCode(ctx, code!!) }
            }
            Divider1()
            Text(
                "Share this code with a friend so they can add you. Adding is instant and mutual.",
                fontSize = 13.sp, color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }

        // ---- add a friend ----
        GroupLabel2("Add a friend")
        Card2 {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(AppIcons.PersonAdd, null, Modifier.size(22.dp), tint = c.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (app.addFriendInput.isEmpty()) Text("Enter their 6-char code", fontSize = 15.sp, color = c.onSurfaceVariant)
                    BasicTextField(
                        value = app.addFriendInput,
                        onValueChange = { app.addFriendInput = it.uppercase().take(6) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, fontFamily = Mono, letterSpacing = 2.sp, color = c.onSurface),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { app.addFriend() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val ready = app.addFriendInput.length == 6
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (ready) c.primary else c.surfaceCHighest)
                        .clickable(enabled = ready) { app.addFriend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Add, "Add", Modifier.size(24.dp), tint = if (ready) c.onPrimary else c.onSurfaceVariant)
                }
            }
        }

        app.friendsNotice?.let { note ->
            Text(note, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.primary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        }

        // ---- friends list ----
        GroupLabel2("Your friends")
        if (app.friendsList.isEmpty()) {
            EmptyLine(if (app.friendsError != null) (app.friendsError ?: "") else "No friends yet — share your code to get started.")
        } else {
            Card2 {
                app.friendsList.forEachIndexed { i, f ->
                    if (i > 0) Divider1()
                    FriendRow(f) { app.removeFriend(f.friend_code) }
                }
            }
        }

        // ---- recommendations ----
        GroupLabel2("Recommended to you")
        if (app.recommendations.isEmpty()) {
            EmptyLine("No recommendations yet. When a friend sends you an SCP it shows up here.")
        } else {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                app.recommendations.forEach { rec -> RecCard(rec) { app.openRecommendation(rec) } }
            }
        }
    }
}

@Composable
private fun FriendRow(f: FriendsApi.Friend, onRemove: () -> Unit) {
    val c = LocalScpScheme.current
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Group, null, Modifier.size(20.dp), tint = c.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(f.name.ifBlank { f.friend_code }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
            if (f.name.isNotBlank()) Text(f.friend_code, fontFamily = Mono, fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Delete, "Remove friend", Modifier.size(20.dp), tint = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecCard(rec: FriendsApi.Rec, onClick: () -> Unit) {
    val c = LocalScpScheme.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceCLow)
            .border(1.dp, c.outlineVariant, RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            "FROM ${(rec.from_name.ifBlank { rec.from_code }).uppercase()}",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = c.primary,
        )
        Text(rec.scp_number.ifBlank { rec.scp_title }, fontFamily = Mono, fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Text(rec.scp_title.ifBlank { rec.scp_number }, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.padding(top = 2.dp))
        if (rec.note.isNotBlank()) {
            Text("“${rec.note}”", fontSize = 14.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(AppIcons.Article, null, Modifier.size(16.dp), tint = c.primary)
            Text("Open in reader", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.primary)
        }
    }
}

/** Bottom sheet that lets the reader recommend the current SCP to a friend. */
@Composable
fun RecommendSheet(app: AppState) {
    val c = LocalScpScheme.current
    // scrim — tap outside to dismiss
    Box(Modifier.fillMaxSize().background(c.scrim).clickable { app.closeRecommendSheet() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clickable(enabled = false) {}.clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(c.surfaceContainer).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
        ) {
            Box(Modifier.padding(bottom = 16.dp).size(width = 34.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.outline.copy(alpha = 0.5f)).align(Alignment.CenterHorizontally))

            Text("Recommend to a friend", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
            app.readerItem?.let {
                Text(it.number + " · " + it.title, fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            }

            // optional note
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceCLow)
                    .border(1.dp, c.outlineVariant, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (app.recommendNote.isEmpty()) Text("Add a note (optional)", fontSize = 14.sp, color = c.onSurfaceVariant)
                BasicTextField(
                    value = app.recommendNote,
                    onValueChange = { app.recommendNote = it.take(280) },
                    textStyle = TextStyle(fontSize = 14.sp, color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))

            if (app.friendsList.isEmpty()) {
                Text(
                    if (app.friendsLoading) "Loading your friends…" else "You have no friends yet — add one from the Friends tab.",
                    fontSize = 14.sp, color = c.onSurfaceVariant,
                )
            } else {
                Text("SEND TO", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                app.friendsList.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { app.sendRecommendation(f.friend_code) }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Group, null, Modifier.size(19.dp), tint = c.onSecondaryContainer)
                        }
                        Text(f.name.ifBlank { f.friend_code }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface, modifier = Modifier.weight(1f))
                        Icon(AppIcons.Send, null, Modifier.size(20.dp), tint = c.primary)
                    }
                }
            }
        }
    }
}

// ---- small local helpers (kept private to this screen) ----

@Composable
private fun GroupLabel2(text: String) {
    val c = LocalScpScheme.current
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = c.primary,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Card2(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = LocalScpScheme.current
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(c.surfaceCLow).border(1.dp, c.outlineVariant, RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable
private fun EmptyLine(text: String) {
    val c = LocalScpScheme.current
    Text(text, fontSize = 14.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
}

@Composable
private fun CircleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalScpScheme.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (enabled) c.onSurface else c.onSurfaceVariant)
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("friend code", text))
}

private fun shareCode(ctx: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Add me on SCP Reader — my friend code is $code")
    }
    ctx.startActivity(Intent.createChooser(send, "Share friend code"))
}
