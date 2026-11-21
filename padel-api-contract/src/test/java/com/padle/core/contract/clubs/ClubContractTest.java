package com.padle.core.contract.clubs;

import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClubContractTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void responseKeepsGoldenWireFieldsIncludingNullableKeysAndUnicode() {
        var wire = """
                {"id":7,"name":"Клуб <&>","address":null,"city":"Mendoza","description":null,
                 "logoUrl":null,"mapUrl":null,"websiteUrl":null,"canonicalUrl":"https://example.test/clubs/7"}
                """;
        var club = json.readValue(wire, Club.class);
        assertThat(json.readTree(json.writeValueAsString(club))).isEqualTo(json.readTree(wire));
    }

    @Test
    void responseAlwaysEmitsNullableKeysEvenWhenTheyWereAbsentInInput() {
        var absent = json.readValue("{\"id\":7,\"canonicalUrl\":\"https://example.test/clubs/7\"}", Club.class);
        var serialized = json.readTree(json.writeValueAsString(absent));
        assertThat(serialized.size()).isEqualTo(9);
        for (String field : new String[]{"name", "address", "city", "description", "logoUrl", "mapUrl", "websiteUrl"}) {
            assertThat(serialized.has(field)).isTrue();
            assertThat(serialized.path(field).isNull()).isTrue();
        }
    }

    @Test
    void nullablePropertiesAreValidButIdAndCanonicalAreRequired() {
        try (var factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            var club = json.readValue("{\"id\":7,\"canonicalUrl\":\"https://example.test/clubs/7\"}", Club.class);
            assertThat(factory.getValidator().validate(club)).isEmpty();
            club.setId(null);
            club.setCanonicalUrl(null);
            assertThat(factory.getValidator().validate(club))
                    .extracting(value -> value.getPropertyPath().toString()).containsExactlyInAnyOrder("id", "canonicalUrl");
        }
    }

    @Test
    void legacyDiscoveryRepresentationHasExactlyTheCanonicalClubSlice() throws Exception {
        var module = Path.of(System.getProperty("user.dir"));
        var canonical = json.readTree(module.resolve("src/main/openapi/public-clubs.openapi.json").toFile());
        var legacy = json.readTree(module.resolve("../docs/frontend-react/contract/public-discovery.openapi.json").toFile());
        assertThat(canonical.path("paths").size()).isEqualTo(2);
        for (String path : canonical.path("paths").propertyNames()) {
            assertThat(legacy.path("paths").path(path)).isEqualTo(canonical.path("paths").path(path));
        }
        for (String name : canonical.path("components").path("schemas").propertyNames()) {
            assertThat(legacy.path("components").path("schemas").path(name))
                    .isEqualTo(canonical.path("components").path("schemas").path(name));
        }
    }
}
