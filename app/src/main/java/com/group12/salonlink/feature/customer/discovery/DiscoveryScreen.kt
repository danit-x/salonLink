// Covers: US-02, FR-02 (discover barbers), US-04 (next appointment) · Figma: "Discovery · SalonLink"
package com.group12.salonlink.feature.customer.discovery

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.BrandMark
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassButton
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.IconBubble
import com.group12.salonlink.core.designsystem.component.SalonPhoto
import com.group12.salonlink.core.designsystem.component.SalonTag
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.component.SectionHeader
import com.group12.salonlink.core.designsystem.theme.CardShape
import com.group12.salonlink.core.designsystem.theme.HeroShape
import com.group12.salonlink.core.designsystem.theme.PillShape
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.feature.customer.booking.BarberSummaryRow
import com.group12.salonlink.feature.customer.booking.BookingFlowPreviewData
import com.group12.salonlink.feature.customer.booking.formatRating
import com.group12.salonlink.feature.customer.booking.messageRes

/** Where Discovery can take the user. The Route composable turns these into navigation. */
data class DiscoveryActions(
    val onBarberClick: (BarberCard) -> Unit = {},
    val onBook: (BarberCard) -> Unit = {},
    val onBookAgain: (Booking) -> Unit = {},
    val onOpenBooking: (Booking) -> Unit = {},
    val onOpenConcierge: () -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
    val onOpenQueue: (Booking) -> Unit = {},
)

/** Bell with a coral dot while there are unread notifications. */
@Composable
private fun NotificationBell(unreadCount: Int, onClick: () -> Unit) {
    val description = if (unreadCount > 0) stringResource(R.string.cd_notifications_unread, unreadCount)
    else stringResource(R.string.cd_notifications)
    Box {
        com.group12.salonlink.core.designsystem.component.GlassIconButton(
            icon = R.drawable.ic_bell,
            contentDescription = description,
            onClick = onClick,
        )
        if (unreadCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(9.dp)
                    .background(SalonTheme.colors.coral, CircleShape),
            )
        }
    }
}

/** The category bubbles, in Figma order. null = "All styles". */
private data class CategoryItem(val category: ServiceCategory?, @StringRes val label: Int, @DrawableRes val icon: Int)

private val categoryItems = listOf(
    CategoryItem(ServiceCategory.HAIRCUT, R.string.category_haircuts, R.drawable.ic_scissors),
    CategoryItem(ServiceCategory.COLOUR, R.string.category_colour, R.drawable.ic_palette),
    CategoryItem(ServiceCategory.BEARD, R.string.category_beard, R.drawable.ic_beard),
    CategoryItem(null, R.string.category_all, R.drawable.ic_sparkles),
)

/**
 * Customer home. Bell, favourites, distance, "Queue" and "Waitlist" from the Figma frame are
 * left out until their phases (5 and 7), so nothing on screen is a dead button.
 */
@Composable
fun DiscoveryScreen(
    state: DiscoveryUiState,
    onEvent: (DiscoveryEvent) -> Unit,
    actions: DiscoveryActions,
    unreadCount: Int = 0,
) {
    Column(Modifier.fillMaxSize()) {
        SalonTopBar(
            title = stringResource(R.string.app_name),
            leading = { BrandMark() },
            actions = { NotificationBell(unreadCount, actions.onOpenNotifications) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Welcome(state)
            SearchBar(query = state.query, onQueryChange = { onEvent(DiscoveryEvent.QueryChanged(it)) })
            CategoryRow(selected = state.category, onSelect = { onEvent(DiscoveryEvent.CategorySelected(it)) })
            BarbersSection(state, actions)
            AskEntry(onClick = actions.onOpenConcierge)
            state.upcoming?.let { UpcomingSection(it, state, actions.onOpenBooking) }
            state.bookingsError?.let { FormBanner(stringResource(R.string.discovery_bookings_error), BannerTone.ERROR) }
            QuickActions(state, actions)
            ConciergeInvitation(onClick = actions.onOpenConcierge)
        }
    }
}

@Composable
private fun Welcome(state: DiscoveryUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.area?.let { area ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_map_pin),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(area, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.firstName.isNotEmpty()) {
                val greeting = when (state.greeting) {
                    SalonTime.Greeting.MORNING -> R.string.greeting_morning
                    SalonTime.Greeting.AFTERNOON -> R.string.greeting_afternoon
                    SalonTime.Greeting.EVENING -> R.string.greeting_evening
                }
                Text(
                    text = stringResource(greeting, state.firstName),
                    style = MaterialTheme.typography.labelMedium,
                    color = SalonTheme.colors.coral,
                )
            }
            Text(stringResource(R.string.discovery_headline), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** Glass pill search field. Filters by barber, salon, specialty or service name as you type. */
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val placeholder = stringResource(R.string.discovery_search_placeholder)
    GlassSurface(
        shape = PillShape,
        contentPadding = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = placeholder },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(selected: ServiceCategory?, onSelect: (ServiceCategory?) -> Unit) {
    val colors = SalonTheme.colors
    val bubbleColors = listOf(colors.blueContainer, colors.peachContainer, colors.peachContainer, colors.lavenderContainer)
    val tints = listOf(MaterialTheme.colorScheme.primary, colors.coral, colors.coral, colors.violet)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        categoryItems.forEachIndexed { index, item ->
            val isSelected = item.category == selected
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(item.category) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                IconBubble(
                    icon = item.icon,
                    containerColor = bubbleColors[index],
                    iconTint = tints[index],
                    modifier = if (isSelected) Modifier.border(1.5.dp, tints[index], CircleShape) else Modifier,
                )
                Text(
                    text = stringResource(item.label),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (isSelected) tints[index] else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** "Your next great cut": a big photo card for the best match, then compact rows for the rest. */
@Composable
private fun BarbersSection(state: DiscoveryUiState, actions: DiscoveryActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.discovery_featured_title))
        val error = state.catalogueError
        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(242.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null -> FormBanner(stringResource(error.messageRes()), BannerTone.ERROR)
            state.barbers.isEmpty() -> FormBanner(
                stringResource(if (state.isFiltering) R.string.discovery_no_match else R.string.discovery_no_barbers),
                BannerTone.INFO,
            )
            else -> {
                FeaturedBarberCard(state.barbers.first(), onClick = { actions.onBarberClick(state.barbers.first()) })
                val others = state.barbers.drop(1)
                if (others.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.discovery_more_barbers),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    others.forEach { card ->
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CardShape)
                                .clickable { actions.onBarberClick(card) },
                        ) {
                            BarberSummaryRow(
                                staff = card.staff,
                                detail = stringResource(R.string.salon_and_area, card.salon.name, card.salon.area) +
                                    card.match?.let { " · " + stringResource(R.string.match_percent, it) }.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Photo card with a dark gradient at the bottom so the white text stays readable. */
@Composable
private fun FeaturedBarberCard(card: BarberCard, onClick: () -> Unit) {
    val colors = SalonTheme.colors
    val staff = card.staff
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(242.dp)
            .clip(HeroShape)
            .clickable(onClick = onClick),
    ) {
        SalonPhoto(
            url = staff.photoUrl,
            name = staff.name,
            contentDescription = stringResource(R.string.cd_photo_of, staff.name),
            initialsSize = MaterialTheme.typography.displaySmall.fontSize,
            modifier = Modifier.fillMaxSize(),
        )
        // Scrim: transparent at the top, darker at the bottom behind the text.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))),
        )
        SalonTag(
            text = card.match?.let { stringResource(R.string.discovery_tag_best_match, it) }
                ?: stringResource(R.string.discovery_tag_top_rated),
            containerColor = colors.tealContainer,
            contentColor = colors.teal,
            icon = R.drawable.ic_sparkles,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(staff.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                text = stringResource(R.string.salon_with_area, card.salon.name, card.salon.area),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.81f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (card.fromPrice != null) {
                        stringResource(R.string.discovery_featured_meta, formatRating(staff.rating), staff.reviewCount, Money.formatLkr(card.fromPrice))
                    } else {
                        stringResource(R.string.rating_value, formatRating(staff.rating))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.discovery_see_times),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = Color.White.copy(alpha = 0.88f),
                )
            }
        }
    }
}

/**
 * "Ask SalonLink" entry. The AI concierge is on hold, so this opens the Concierge tab, which
 * explains that booking by chat is coming later.
 */
@Composable
private fun AskEntry(onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.discovery_ask_title), style = MaterialTheme.typography.labelMedium, color = SalonTheme.colors.coral)
        GlassSurface(
            shape = PillShape,
            contentPadding = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(PillShape)
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = SalonTheme.colors.violet,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = stringResource(R.string.discovery_ask_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpcomingSection(booking: Booking, state: DiscoveryUiState, onOpen: (Booking) -> Unit) {
    val whenLabel = when (state.upcomingDay) {
        UpcomingDay.TODAY -> stringResource(R.string.label_today)
        UpcomingDay.TOMORROW -> stringResource(R.string.label_tomorrow)
        UpcomingDay.LATER -> SalonTime.formatShortDate(booking.start)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = stringResource(R.string.discovery_upcoming_title),
            actionLabel = whenLabel,
            actionColor = SalonTheme.colors.coral,
            onAction = { onOpen(booking) },
        )
        GlassSurface(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable { onOpen(booking) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    icon = R.drawable.ic_calendar_days,
                    containerColor = SalonTheme.colors.peachContainer,
                    iconTint = SalonTheme.colors.coral,
                    size = 44.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(booking.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    state.upcomingSalon?.let { salon ->
                        Text(
                            text = stringResource(R.string.salon_with_area, salon.name, salon.address),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.datetime_selected,
                            SalonTime.formatShortDate(booking.start),
                            SalonTime.formatRange(booking.start, booking.end),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.label_total), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.formatLkr(booking.totalPrice), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** "Book" opens the best-rated barber; "Book Again" repeats the last appointment's services. */
@Composable
private fun QuickActions(state: DiscoveryUiState, actions: DiscoveryActions) {
    val featured = state.featured
    val last = state.lastBooking
    if (featured == null && last == null) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = stringResource(R.string.discovery_quick_actions))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (featured != null) {
                GradientButton(
                    text = stringResource(R.string.action_book),
                    onClick = { actions.onBook(featured) },
                    trailingIcon = R.drawable.ic_calendar_days,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 48.dp),
                )
            }
            val today = state.upcoming?.takeIf { state.upcomingDay == UpcomingDay.TODAY }
            if (today != null) {
                GlassButton(
                    text = stringResource(R.string.action_queue),
                    onClick = { actions.onOpenQueue(today) },
                    leadingIcon = R.drawable.ic_sparkles,
                    containerColor = SalonTheme.colors.peachContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            if (last != null) {
                GlassButton(
                    text = stringResource(R.string.action_book_again),
                    onClick = { actions.onBookAgain(last) },
                    leadingIcon = R.drawable.ic_arrow_left,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Lavender card with the concierge orb (Figma "Concierge invitation"). Opens the Concierge tab. */
@Composable
private fun ConciergeInvitation(onClick: () -> Unit) {
    val colors = SalonTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, CardShape, ambientColor = colors.shadowPrimary, spotColor = colors.shadowPrimary)
            .clip(CardShape)
            .background(colors.selectedGradient)
            .border(1.dp, Color.White, CardShape)
            .clickable(onClick = onClick)
            .padding(17.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .background(colors.conciergeOrb, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = stringResource(R.string.discovery_concierge_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.violet,
            )
            Text(
                text = stringResource(R.string.discovery_concierge_body),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_up_right),
            contentDescription = null,
            tint = colors.violet,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8, heightDp = 1300)
@Composable
private fun DiscoveryPreview() {
    val card = BarberCard(
        salon = BookingFlowPreviewData.salon,
        staff = BookingFlowPreviewData.kasun,
        fromPrice = 1500,
        categories = setOf(ServiceCategory.HAIRCUT, ServiceCategory.BEARD),
        serviceNames = listOf("Haircut", "Beard Trim"),
    )
    SalonLinkTheme {
        DiscoveryScreen(
            state = DiscoveryUiState(
                loading = false, firstName = "Kavindu", greeting = SalonTime.Greeting.AFTERNOON,
                area = "Colombo 05", barbers = listOf(card), featured = card,
            ),
            onEvent = {},
            actions = DiscoveryActions(),
        )
    }
}
