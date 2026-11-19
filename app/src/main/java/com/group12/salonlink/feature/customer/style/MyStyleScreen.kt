// Covers: US-10, FR-12, NFR-07 · Figma: "My Style Profile" (tags, moodboard, note, language)
package com.group12.salonlink.feature.customer.style

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassButton
import com.group12.salonlink.core.designsystem.component.GlassIconButton
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonTag
import com.group12.salonlink.core.designsystem.component.SalonTextField
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.component.SectionHeader
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.locale.AppLanguage
import com.group12.salonlink.core.locale.LanguageStore
import com.group12.salonlink.domain.model.PhotoLabel
import com.group12.salonlink.domain.model.Role as UserRole
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StylePhoto
import com.group12.salonlink.domain.model.StyleProfile
import com.group12.salonlink.domain.model.StyleTag
import com.group12.salonlink.domain.model.UserProfile
import com.group12.salonlink.feature.customer.booking.messageRes
import com.group12.salonlink.feature.owner.LabeledSwitch

/**
 * "My style": style tags, a note for the barber (shared only with consent), a favourite barber
 * ranked by match, the private moodboard, the app language and the account / sign-out.
 * Stateless: everything comes from [state]; the photo picker and language switch are the only
 * Android pieces, wired here because they need the Activity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyStyleScreen(
    state: MyStyleUiState,
    onEvent: (MyStyleEvent) -> Unit,
    onSignOut: () -> Unit,
) {
    PhotoPicker(state, onEvent)
    state.askConsentFor?.let { ConsentDialog(onEvent) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SalonTopBar(title = stringResource(R.string.style_title), subtitle = state.user?.name)
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                stringResource(R.string.style_headline),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            state.error?.let { FormBanner(stringResource(it.messageRes()), BannerTone.ERROR) }
            when (val m = state.message) {
                StyleMessage.Saved -> FormBanner(stringResource(R.string.style_saved), BannerTone.INFO)
                StyleMessage.PhotoAdded -> FormBanner(stringResource(R.string.style_photo_added), BannerTone.INFO)
                is StyleMessage.Failed -> FormBanner(stringResource(m.error.messageRes()), BannerTone.ERROR)
                null -> Unit
            }

            // --- Tags ---
            SectionHeader(stringResource(R.string.style_tags_title))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StyleTag.entries.forEach { tag ->
                    FilterChip(
                        selected = tag in state.draft.tags,
                        onClick = { onEvent(MyStyleEvent.ToggleTag(tag)) },
                        label = { Text(stringResource(tag.labelRes())) },
                    )
                }
            }

            // --- Note + consent ---
            SalonTextField(
                value = state.draft.note,
                onValueChange = { onEvent(MyStyleEvent.NoteChanged(it)) },
                label = stringResource(R.string.style_note_label),
                placeholder = stringResource(R.string.style_note_placeholder),
                supportingText = stringResource(R.string.style_note_counter, state.draft.note.length, StyleProfile.MAX_NOTE),
            )
            LabeledSwitch(
                label = stringResource(R.string.style_share_label),
                checked = state.draft.shareWithBarber,
                onChange = { onEvent(MyStyleEvent.ShareChanged(it)) },
            )
            Text(
                stringResource(if (state.draft.shareWithBarber) R.string.style_share_on else R.string.style_share_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Favourite barber, best match first ---
            if (state.barbers.isNotEmpty()) {
                SectionHeader(stringResource(R.string.style_barber_title))
                Text(
                    stringResource(if (state.draft.tags.isEmpty()) R.string.style_barber_hint_no_tags else R.string.style_barber_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.barbers.forEach { choice ->
                    BarberRow(choice, selected = state.draft.preferredStaffId == choice.staff.id) {
                        onEvent(MyStyleEvent.PreferredBarber(if (state.draft.preferredStaffId == choice.staff.id) null else choice.staff.id))
                    }
                }
            }

            GradientButton(
                text = stringResource(R.string.style_save),
                onClick = { onEvent(MyStyleEvent.Save) },
                enabled = state.dirty && !state.saving,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Moodboard ---
            SectionHeader(stringResource(R.string.style_moodboard_title))
            Text(
                stringResource(R.string.style_moodboard_body, MyStyleUiState.MAX_PHOTOS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(
                    text = stringResource(R.string.style_add_inspiration),
                    onClick = { onEvent(MyStyleEvent.AddPhoto(PhotoLabel.INSPIRATION)) },
                    leadingIcon = R.drawable.ic_image_plus,
                    enabled = state.canAddPhoto,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = stringResource(R.string.style_add_result),
                    onClick = { onEvent(MyStyleEvent.AddPhoto(PhotoLabel.PREVIOUS_RESULT)) },
                    leadingIcon = R.drawable.ic_scissors,
                    enabled = state.canAddPhoto,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.addingPhoto) FormBanner(stringResource(R.string.style_photo_adding), BannerTone.INFO)
            PhotoGrid(state.photos, onDelete = { onEvent(MyStyleEvent.DeletePhoto(it)) })

            // --- Language ---
            SectionHeader(stringResource(R.string.language_title))
            LanguagePicker()

            // --- Account ---
            state.user?.let { AccountCard(it) }
            GlassButton(text = stringResource(R.string.sign_out), onClick = onSignOut, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Opens the system photo picker when the ViewModel asks for it (no storage permission needed). */
@Composable
private fun PhotoPicker(state: MyStyleUiState, onEvent: (MyStyleEvent) -> Unit) {
    var pendingLabel by rememberSaveable { mutableLabel() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onEvent(MyStyleEvent.PhotoPicked(uri?.toString(), PhotoLabel.valueOf(pendingLabel)))
    }
    LaunchedEffect(state.pickPhotoLabel) {
        val label = state.pickPhotoLabel ?: return@LaunchedEffect
        pendingLabel = label.name
        onEvent(MyStyleEvent.PickerOpened)
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

private fun mutableLabel() = androidx.compose.runtime.mutableStateOf(PhotoLabel.INSPIRATION.name)

@Composable
private fun ConsentDialog(onEvent: (MyStyleEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(MyStyleEvent.ConsentDismissed) },
        title = { Text(stringResource(R.string.style_consent_title)) },
        text = { Text(stringResource(R.string.style_consent_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(MyStyleEvent.ConsentGiven) }) { Text(stringResource(R.string.style_consent_agree)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(MyStyleEvent.ConsentDismissed) }) { Text(stringResource(R.string.action_back)) }
        },
    )
}

@Composable
private fun BarberRow(choice: BarberChoice, selected: Boolean, onClick: () -> Unit) {
    val colors = SalonTheme.colors
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(choice.staff.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    choice.staff.specialties.joinToString(" · ").ifEmpty { choice.salonName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            choice.match?.let {
                SalonTag(stringResource(R.string.match_percent, it), colors.lavenderContainer, colors.violet)
            }
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<StylePhoto>, onDelete: (String) -> Unit) {
    if (photos.isEmpty()) {
        FormBanner(stringResource(R.string.style_moodboard_empty), BannerTone.INFO)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { photo -> PhotoTile(photo, onDelete, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: StylePhoto, onDelete: (String) -> Unit, modifier: Modifier) {
    val label = stringResource(if (photo.label == PhotoLabel.INSPIRATION) R.string.style_label_inspiration else R.string.style_label_result)
    Box(modifier.aspectRatio(0.8f).clip(RoundedCornerShape(20.dp))) {
        AsyncImage(
            model = photo.jpeg,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        GlassIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.cd_delete_photo),
            onClick = { onDelete(photo.id) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(36.dp),
        )
    }
}

/** English / සිංහල / தமிழ் / phone default. Saving recreates the screen in the new language. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val current = LanguageStore.get(context)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { language ->
            FilterChip(
                selected = language == current,
                onClick = {
                    if (language != current) {
                        LanguageStore.set(context, language)
                        (context as? Activity)?.recreate()
                    }
                },
                // Each language is written in itself, so it can be found whatever is shown now.
                label = { Text(stringResource(language.labelRes())) },
            )
        }
    }
}

@Composable
private fun AccountCard(user: UserProfile) {
    val colors = SalonTheme.colors
    GlassSurface(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(painterResource(R.drawable.ic_user_round), contentDescription = null, tint = colors.violet)
            Column(Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleSmall)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}

@StringRes
fun StyleTag.labelRes(): Int = when (this) {
    StyleTag.FADE -> R.string.style_tag_fade
    StyleTag.CURLY -> R.string.style_tag_curly
    StyleTag.COLOUR -> R.string.style_tag_colour
    StyleTag.BEARD -> R.string.style_tag_beard
    StyleTag.LONG_HAIR -> R.string.style_tag_long
    StyleTag.TREATMENT -> R.string.style_tag_treatment
    StyleTag.CLASSIC -> R.string.style_tag_classic
}

@StringRes
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.SINHALA -> R.string.language_sinhala
    AppLanguage.TAMIL -> R.string.language_tamil
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun MyStylePreview() {
    val kasun = Staff("kasun", "Kasun Perera", "", listOf("Fade", "Beard Styling"), 4.9, 128, 8, null, emptyList(),
        com.group12.salonlink.domain.model.WeeklyHours(emptyMap()))
    SalonLinkTheme {
        MyStyleScreen(
            state = MyStyleUiState(
                loading = false,
                user = UserProfile("u", "Kavindu Perera", "kavindu@example.com", null, UserRole.CUSTOMER),
                draft = StyleProfile(tags = setOf(StyleTag.FADE, StyleTag.BEARD), note = "Low fade, keep the top", shareWithBarber = true),
                dirty = true,
                barbers = listOf(BarberChoice(kasun, "Aura Salon", 100)),
            ),
            onEvent = {},
            onSignOut = {},
        )
    }
}
