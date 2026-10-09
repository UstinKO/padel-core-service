package com.padle.core.padelcoreservice.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.padle.core.padelcoreservice.security.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

// LFPT-0518: StructuredAlert — чистая логика форматирования алерта, без Spring-контекста.
class StructuredAlertTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger("StructuredAlertTest");
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void warn_rendersTitleEventFieldsRequestIdAndTimeInOrder() {
        MDC.put(RequestIdFilter.MDC_KEY, "abc12345");

        StructuredAlert.of(AlertSeverity.USER_VALIDATION_ERROR, "Ошибка регистрации на турнир", "TOURNAMENT_REGISTRATION")
                .field("Tournament ID", 184L)
                .field("User ID", 928L)
                .field("Error", "Ya estás registrado")
                .warn(logger);

        String message = appender.list.get(0).getFormattedMessage();

        assertThat(message).startsWith(AlertSeverity.USER_VALIDATION_ERROR.emoji() + " Ошибка регистрации на турнир");
        assertThat(message).containsSubsequence(
                "Event: TOURNAMENT_REGISTRATION",
                "Tournament ID: 184",
                "User ID: 928",
                "Error: Ya estás registrado",
                "Request ID: abc12345",
                "Time: "
        );
    }

    @Test
    void warn_withoutRequestIdInMdc_fallsBackToDash() {
        StructuredAlert.of(AlertSeverity.SECURITY_WARNING, "Title", "EVENT").warn(logger);

        assertThat(appender.list.get(0).getFormattedMessage()).contains("Request ID: —");
    }

    @Test
    void field_nullValue_isSkipped() {
        StructuredAlert.of(AlertSeverity.SECURITY_WARNING, "Title", "EVENT")
                .field("Present", "value")
                .field("Absent", null)
                .warn(logger);

        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("Present: value")
                .doesNotContain("Absent");
    }

    @Test
    void warn_setsStructuredMdcFlagOnEventAndClearsItAfterward() {
        StructuredAlert.of(AlertSeverity.APPLICATION_ERROR, "Title", "EVENT").warn(logger);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMDCPropertyMap()).containsEntry(StructuredAlert.STRUCTURED_MDC_KEY, "true");
        assertThat(MDC.get(StructuredAlert.STRUCTURED_MDC_KEY)).isNull();
    }

    @Test
    void error_withCause_attachesThrowableToEvent() {
        RuntimeException cause = new RuntimeException("boom");

        StructuredAlert.of(AlertSeverity.APPLICATION_ERROR, "Title", "EVENT").error(logger, cause);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void field_escapesHtmlSpecialCharacters() {
        StructuredAlert.of(AlertSeverity.APPLICATION_ERROR, "Title", "EVENT")
                .field("Endpoint", "GET /foo?a=1&b=<script>")
                .warn(logger);

        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("&amp;").contains("&lt;script&gt;").doesNotContain("<script>");
    }
}
