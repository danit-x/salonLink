// Covers: US-02, FR-02 (barber details, specialties, portfolio) · Figma: "Barber profile · Kasun Perera"
package com.group12.salonlink.feature.customer.barber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GradientButton
import com.group12.salonlink.core.designsystem.component.SalonPhoto
import com.group12.salonlink.core.designsystem.component.SalonTag
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.component.SectionHeader
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.util.Money
import com.group12.salonlink.domain.booking.BookingPolicy
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.feature.customer.booking.BookingFlowPreviewData
import com.group12.salonlink.feature.customer.booking.formatRating
import com.group12.salonlink.feature.customer.booking.messageRes

/** Figma's portrait has very round corners (radius 88 on a 248 dp tall photo). */
private val PortraitShape = RoundedCornerShape(88.dp)

/**
 * A barber's profile: portrait, rating, specialties, bio, portfolio and "Book with Kasun".
 * The heart (favourites) and the review quote from Figma are left out until those features
 * exist, so the screen never shows something that doesn't work or isn't real.
 */
@Composable
fun BarberProfileScreen(
    state: BarberProfileUiState,
    onBack: () -> Unit,
    onBook: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SalonTopBar(title = stringResource(R.string.barber_title), onBack = onBack)
        val staff = state.staff
        val salon = state.salon
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            staff == null || salon == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                FormBanner(stringResource((state.error ?: DataError.NOT_FOUND).messageRes()), BannerTone.ERROR)
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screenHorizontal)
                        .padding(top = 14.dp, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    Portrait(staff)
                    Identity(staff, salon)
                    if (staff.portfolioUrls.isNotEmpty()) Portfolio(staff)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GradientButton(
                        text = stringResource(R.string.barber_book_with, staff.firstName),
                        onClick = onBook,
                        trailingIcon = R.drawable.ic_calendar_days,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.fromPrice?.let { price ->
                        Text(
                            text = stringResource(R.string.barber_footer, Money.formatLkr(price), BookingPolicy.FREE_CANCELLATION_HOURS.toInt()),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Big rounded portrait with a pastel accent circle behind its top-right corner. */
@Composable
private fun Portrait(staff: Staff) {
    val colors = SalonTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(248.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-12).dp)
                .size(134.dp)
                .background(colors.selectedGradient, CircleShape),
        )
        SalonPhoto(
            url = staff.photoUrl,
            name = staff.name,
            contentDescription = stringResource(R.string.cd_photo_of, staff.name),
            shape = PortraitShape,
            initialsSize = MaterialTheme.typography.displaySmall.fontSize,
            modifier = Modifier.fillMaxSize(),
        )
        if (staff.yearsExperience > 0) {
            SalonTag(
                text = pluralStringResource(R.plurals.barber_experience, staff.yearsExperience, staff.yearsExperience),
                containerColor = colors.peachContainer,
                contentColor = colors.coral,
                icon = R.drawable.ic_sparkles,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 11.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Identity(staff: Staff, salon: Salon) {
    val colors = SalonTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = staff.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.rating_value, formatRating(staff.rating)),
                style = MaterialTheme.typography.titleSmall,
                color = colors.coral,
            )
        }
        Text(
            text = pluralStringResource(
                R.plurals.barber_salon_reviews,
                staff.reviewCount,
                stringResource(R.string.salon_with_area, salon.name, salon.area),
                staff.reviewCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (staff.specialties.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                staff.specialties.forEachIndexed { index, specialty ->
                    val (background, text) = colors.tagPalette[index % colors.tagPalette.size]
                    SalonTag(text = specialty, containerColor = background, contentColor = text)
                }
            }
        }
        if (staff.bio.isNotBlank()) {
            Text(staff.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "The craft, up close": up to three work photos side by side. */
@Composable
private fun Portfolio(staff: Staff) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = stringResource(R.string.barber_portfolio_title))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            staff.portfolioUrls.take(3).forEachIndexed { index, url ->
                SalonPhoto(
                    url = url,
                    name = staff.name,
                    contentDescription = stringResource(R.string.cd_portfolio_photo, staff.name, index + 1),
                    shape = RoundedCornerShape(22.dp),
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(118.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun BarberProfilePreview() {
    SalonLinkTheme {
        BarberProfileScreen(
            state = BarberProfileUiState(
                loading = false,
                salon = BookingFlowPreviewData.salon,
                staff = BookingFlowPreviewData.kasun,
                fromPrice = 1500,
            ),
            onBack = {},
            onBook = {},
        )
    }
}
