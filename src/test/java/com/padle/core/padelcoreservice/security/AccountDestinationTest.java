package com.padle.core.padelcoreservice.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class AccountDestinationTest {
    @ParameterizedTest
    @ValueSource(strings={"https://evil.test", "//evil.test", "/%2fevil.test", "/%252fevil.test", "/\\evil.test", "/%5cevil.test", "/%0dLocation:x", "/%25252525252fevil.test"})
    void rejectsExternalAndEncodedRedirects(String value) { assertThat(AccountDestination.safe(value)).isEmpty(); }

    @ParameterizedTest
    @ValueSource(strings={"/torneo/42#tournament-registration", "/preview/perfil", "/torneos?lang=ru"})
    void retainsInternalDestinations(String value) { assertThat(AccountDestination.safe(value)).contains(value); }
}
