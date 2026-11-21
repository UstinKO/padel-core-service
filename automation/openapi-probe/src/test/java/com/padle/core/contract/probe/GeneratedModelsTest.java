package com.padle.core.contract.probe;

import com.padle.core.contract.probe.generated.AccountProblem;
import com.padle.core.contract.probe.generated.AccountProfile;
import com.padle.core.contract.probe.generated.DiscoveryClub;
import com.padle.core.contract.probe.generated.DiscoveryTournament;
import com.padle.core.contract.probe.generated.LiveSnapshot;
import com.padle.core.contract.probe.generated.PlayerEntry;
import com.padle.core.contract.probe.generated.ProbeExtendedClub;
import com.padle.core.contract.probe.generated.ProbeFeatures;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedModelsTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final ProbeMapper mapper = Mappers.getMapper(ProbeMapper.class);

    @Test
    void clubPreservesAllNineFieldsIncludingRequiredNullsAndMapStructRoundTrip() {
        var input = """
                {"id":7,"name":"Клуб","address":null,"city":"Mendoza","description":null,
                 "logoUrl":null,"mapUrl":null,"websiteUrl":null,"canonicalUrl":"https://example.test/clubs/7"}
                """;
        var club = json.readValue(input, DiscoveryClub.class);
        var view = mapper.toView(club);

        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.name()).isEqualTo("Клуб");
        assertThat(json.readTree(json.writeValueAsString(mapper.toApi(view))))
                .isEqualTo(json.readTree(input));
    }

    @Test
    void moneyDatesTimeZoneAndCollectionsRoundTripWithoutPrecisionLoss() {
        var input = """
                {"amount":12345678901234567890.125,"day":"2026-11-20",
                 "moment":"2026-11-20T12:34:56-03:00","timeZone":"America/Argentina/Buenos_Aires",
                 "wire":"in-progress","tags":["RU","ES","EN"]}
                """;
        var value = json.readValue(input, ProbeFeatures.class);
        assertThat(value.getAmount()).isEqualByComparingTo(new BigDecimal("12345678901234567890.125"));
        assertThat(value.getDay()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(value.getMoment().toInstant()).isEqualTo(OffsetDateTime.parse("2026-11-20T12:34:56-03:00").toInstant());
        assertThat(ZoneId.of(value.getTimeZone()).getId()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(value.getTags()).containsExactly("RU", "ES", "EN");

        var restored = json.readValue(json.writeValueAsString(value), ProbeFeatures.class);
        assertThat(restored).isEqualTo(value);
        assertThat(json.readTree(json.writeValueAsString(value)).path("wire").asString()).isEqualTo("in-progress");
    }

    @Test
    void tournamentMoneyAndDateMapToNeutralRecord() {
        var tournament = json.readValue("""
                {"price":12345.125,"startsAt":"2026-11-20T15:00:00-03:00",
                 "timeZone":"America/Argentina/Buenos_Aires","type":"AMERICANO_TEAMS"}
                """, DiscoveryTournament.class);
        var view = mapper.toView(tournament);
        assertThat(view.price()).isEqualByComparingTo("12345.125");
        assertThat(view.startsAt()).isEqualTo(tournament.getStartsAt());
        assertThat(view.timeZone()).isEqualTo(tournament.getTimeZone());
        assertThat(json.readTree(json.writeValueAsString(tournament)).path("type").asString())
                .isEqualTo("AMERICANO_TEAMS");
    }

    @Test
    void accountProblemPreservesFieldErrorsAndRetryMetadata() {
        var problem = json.readValue("""
                {"type":"about:blank","title":"Ошибка","status":422,"detail":"Проверьте данные",
                 "instance":"/api/account","code":"VALIDATION_ERROR","retryAfter":12,
                 "blockedUntil":"2026-11-20T15:00:00Z",
                 "fieldErrors":[{"field":"nombre","code":"NotBlank","message":"Укажите имя"}]}
                """, AccountProblem.class);
        var restored = json.readValue(json.writeValueAsString(problem), AccountProblem.class);
        assertThat(restored).isEqualTo(problem);
        assertThat(restored.getFieldErrors()).hasSize(1);
        assertThat(restored.getFieldErrors().get(0).getField()).isEqualTo("nombre");
        assertThat(restored.getRetryAfter()).isEqualTo(12);
    }

    @Test
    void playerEntryPreservesNullableEnumsAndActionCollections() {
        var entry = json.readValue("""
                {"registrationId":3,"version":1,"registrationStatus":"WAITLIST","mode":null,
                 "paymentStatus":null,"participationStatus":"PENDING","participation":[],
                 "availableActions":["WITHDRAW","CANCEL_PAIR"],"cancellations":[]}
                """, PlayerEntry.class);
        var restored = json.readValue(json.writeValueAsString(entry), PlayerEntry.class);
        assertThat(restored).isEqualTo(entry);
        assertThat(restored.getMode()).isNull();
        assertThat(restored.getPaymentStatus()).isNull();
        assertThat(restored.getAvailableActions()).extracting(Object::toString)
                .containsExactly("WITHDRAW", "CANCEL_PAIR");
    }

    @Test
    void externalRefAndSyntheticAllOfPreserveClubAndExtraProperty() {
        var club = json.readValue("""
                {"id":9,"name":"Клуб","canonicalUrl":"https://example.test/clubs/9","marker":"probe"}
                """, ProbeExtendedClub.class);
        assertThat(club.getId()).isEqualTo(9L);
        assertThat(club.getMarker()).isEqualTo("probe");
        assertThat(json.readValue(json.writeValueAsString(club), ProbeExtendedClub.class)).isEqualTo(club);
    }

    @Test
    void jakartaValidationIsPresentOnRequiredNonNullableField() throws NoSuchMethodException {
        assertThat(DiscoveryClub.class.getMethod("getId").isAnnotationPresent(NotNull.class)).isTrue();
        assertThat(DiscoveryClub.class.getMethod("getName").isAnnotationPresent(NotNull.class)).isFalse();
    }

    @Test
    void jakartaValidatorAcceptsNullableClubButRejectsMissingRequiredId() {
        try (var factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            var club = json.readValue("""
                    {"id":7,"canonicalUrl":"https://example.test/clubs/7"}
                    """, DiscoveryClub.class);
            assertThat(factory.getValidator().validate(club)).isEmpty();
            club.setId(null);
            assertThat(factory.getValidator().validate(club))
                    .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("id");
        }
    }

    @Test
    void optionalMissingAndExplicitNullCollapseWithoutPresenceWrapper() {
        var absent = json.readValue("{}", AccountProfile.class);
        var explicitNull = json.readValue("{\"nombre\":null}", AccountProfile.class);
        assertThat(explicitNull).isEqualTo(absent);
        assertThat(json.writeValueAsString(explicitNull)).isEqualTo(json.writeValueAsString(absent));
    }

    @Test
    void clientTemplateDoesNotEnforceServerReadOnlyOrWriteOnlySecurity() {
        var value = json.readValue("""
                {"serverValue":"client-controlled","password":"synthetic-probe-value"}
                """, ProbeFeatures.class);
        assertThat(value.getServerValue()).isEqualTo("client-controlled");
        assertThat(json.readTree(json.writeValueAsString(value)).path("password").asString())
                .isEqualTo("synthetic-probe-value");
    }

    @Test
    void discriminatorFreeLiveOneOfCannotBeAdoptedWithThisConfiguration() {
        assertThatThrownBy(() -> json.readValue("""
                {"metadata":{"eventId":1,"phase":"NOT_STARTED","conductVersion":0,
                 "showLevels":false,"pollSeconds":20},"format":"TEAM_PAIRS","rounds":[]}
                """, LiveSnapshot.class))
                .isInstanceOf(InvalidTypeIdException.class);
    }

    @Test
    void unknownWireEnumIsRejected() {
        assertThatThrownBy(() -> json.readValue("{\"wire\":\"unknown\"}", ProbeFeatures.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
