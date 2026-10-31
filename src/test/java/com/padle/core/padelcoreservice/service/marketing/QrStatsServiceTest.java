package com.padle.core.padelcoreservice.service.marketing;

import com.padle.core.padelcoreservice.exception.InvalidQrPeriodException;
import com.padle.core.padelcoreservice.repository.marketing.QrStatsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QrStatsServiceTest {
    private final QrStatsRepository repository = mock(QrStatsRepository.class);
    private final QrStatsService service = new QrStatsService(repository);

    @Test
    void databaseFailureDoesNotBlockRedirect() {
        doThrow(new DataAccessResourceFailureException("offline"))
                .when(repository).increment(any(), eq("site"));
        assertThatCode(() -> service.recordClick("site")).doesNotThrowAnyException();
    }

    @Test
    void usesBuenosAiresDateAndDefaultMonth() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        service.recordClick("telegram");
        verify(repository).increment(today, "telegram");
        when(repository.findDays(any(), any())).thenReturn(List.of());
        var result = service.stats(null, null);
        assertThat(result.from()).isEqualTo(today.withDayOfMonth(1));
        assertThat(result.to()).isEqualTo(today.withDayOfMonth(today.lengthOfMonth()));
        assertThat(result.site()).isZero();
        assertThat(result.telegram()).isZero();
    }

    @Test
    void aggregatesIndependentDestinations() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        when(repository.findDays(start, start.plusDays(1))).thenReturn(List.of(
                new QrStats.Day(start, 3, 0), new QrStats.Day(start.plusDays(1), 2, 7)));
        var result = service.stats(start, start.plusDays(1));
        assertThat(result.site()).isEqualTo(5);
        assertThat(result.telegram()).isEqualTo(7);
    }

    @Test
    void invalidPeriodNeverQueriesDatabase() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        assertThatThrownBy(() -> service.stats(start, start.minusDays(1)))
                .isInstanceOf(InvalidQrPeriodException.class);
        assertThatThrownBy(() -> service.stats(start, start.plusDays(366)))
                .isInstanceOf(InvalidQrPeriodException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void unavailableReportIsNotReportedAsZero() {
        when(repository.findDays(any(), any())).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> service.stats(null, null)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void filtersHeadCrawlerAndPrefetchButCountsTelegramInAppBrowser() {
        assertThat(QrRequestPolicy.shouldCount("GET", "Mozilla/5.0 Telegram-Android/11", null, null)).isTrue();
        assertThat(QrRequestPolicy.shouldCount("HEAD", "Mozilla/5.0", null, null)).isFalse();
        assertThat(QrRequestPolicy.shouldCount("GET", "TelegramBot", null, null)).isFalse();
        assertThat(QrRequestPolicy.shouldCount("GET", "facebookexternalhit/1.1", null, null)).isFalse();
        assertThat(QrRequestPolicy.shouldCount("GET", null, "prefetch", null)).isFalse();
        assertThat(QrRequestPolicy.shouldCount("GET", null, null, "prefetch;prerender")).isFalse();
        assertThat(QrRequestPolicy.shouldCount("GET", null, null, null)).isTrue();
    }
}
