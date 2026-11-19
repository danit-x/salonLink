// Covers: FR-12, T08 (live queue + ETA for booked and walk-in customers) · Figma: "Live queue · SalonLink" (loosely)
package com.group12.salonlink.feature.customer.queue

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.component.BannerTone
import com.group12.salonlink.core.designsystem.component.FormBanner
import com.group12.salonlink.core.designsystem.component.GlassSurface
import com.group12.salonlink.core.designsystem.component.SalonTopBar
import com.group12.salonlink.core.designsystem.theme.SalonTheme
import com.group12.salonlink.core.designsystem.theme.Spacing
import com.group12.salonlink.core.navigation.LiveQueueRoute
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.BookingType
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.QueueBoard
import com.group12.salonlink.domain.queue.QueueEta
import com.group12.salonlink.domain.queue.QueueEtaCalculator
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.QueueRepository
import com.group12.salonlink.feature.customer.booking.message
import com.group12.salonlink.feature.customer.booking.toDataError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class LiveQueueUiState(
    val loading: Boolean = true,
    val booking: Booking? = null,
    val board: QueueBoard? = null,
    val eta: QueueEta? = null,
    val isToday: Boolean = true,
    val error: DataError? = null,
)

/**
 * My place in today's queue, recalculated when the salon updates the board and every 30 s
 * (so "about 12 min" counts down even when nothing else changes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = LiveQueueViewModel.Factory::class)
class LiveQueueViewModel @AssistedInject constructor(
    @Assisted args: LiveQueueRoute,
    bookingRepository: BookingRepository,
    queueRepository: QueueRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(args: LiveQueueRoute): LiveQueueViewModel
    }

    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MS)
        }
    }

    private val booking = bookingRepository.observeBooking(args.salonId, args.bookingId)

    private val board = booking.flatMapLatest { b ->
        if (b == null) flowOf(null) else queueRepository.observeBoard(args.salonId, b.staffId)
    }

    val uiState: StateFlow<LiveQueueUiState> = combine(booking, board, ticker) { b, board, now ->
        LiveQueueUiState(
            loading = false,
            booking = b,
            board = board,
            eta = board?.let { QueueEtaCalculator.eta(it.entries, args.bookingId, now) },
            isToday = b != null && SalonTime.localDateOf(b.start) == SalonTime.localDateOf(now),
            error = if (b == null) DataError.NOT_FOUND else null,
        )
    }.catch { emit(LiveQueueUiState(loading = false, error = it.toDataError())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveQueueUiState())

    private companion object {
        const val TICK_MS = 30_000L
    }
}

@Composable
fun LiveQueueScreen(state: LiveQueueUiState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SalonTopBar(title = stringResource(R.string.queue_title), onBack = onBack)
        val booking = state.booking
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                booking == null -> FormBanner((state.error ?: DataError.NOT_FOUND).message(), BannerTone.ERROR)
                !state.isToday -> FormBanner(stringResource(R.string.queue_not_today), BannerTone.INFO)
                state.eta == null -> FormBanner(
                    stringResource(R.string.queue_unavailable, SalonTime.formatTime(booking.start)),
                    BannerTone.INFO,
                )
                else -> QueueDetails(state.eta, state.board)
            }
        }
    }
}

@Composable
private fun QueueDetails(eta: QueueEta, board: QueueBoard?) {
    val colors = SalonTheme.colors
    // The ring fills up as people ahead are served: 1 / (position) of the way when nobody is ahead.
    val progress = if (eta.done || eta.inService) 1f else 1f / eta.position
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        val track = colors.hairline
        val brush = colors.brandGradient
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(brush, -90f, 360f * progress, false, Offset.Zero, Size(size.width, size.height), style = stroke)
        }
        Text(
            text = when {
                eta.done -> stringResource(R.string.queue_done)
                eta.inService -> stringResource(R.string.queue_in_chair)
                else -> stringResource(R.string.queue_position, eta.position)
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = colors.violet,
        )
    }
    if (!eta.done && !eta.inService) {
        Text(
            text = if (eta.waitMinutes <= 2) stringResource(R.string.queue_eta_now)
            else stringResource(R.string.queue_eta, SalonTime.formatTime(eta.estimatedStart), eta.waitMinutes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
    if (eta.ahead.isNotEmpty()) {
        Text(stringResource(R.string.queue_ahead_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
        eta.ahead.forEach { entry ->
            GlassSurface(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Text(
                    text = when {
                        entry.status == BookingStatus.IN_SERVICE -> stringResource(R.string.queue_ahead_in_chair)
                        entry.type == BookingType.WALK_IN -> stringResource(R.string.queue_ahead_walk_in, entry.durationMin)
                        else -> stringResource(R.string.queue_ahead_booked, SalonTime.formatTime(entry.start), entry.durationMin)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
    board?.updatedAt?.let {
        Text(
            stringResource(R.string.queue_updated, SalonTime.formatTime(it)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
