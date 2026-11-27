package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.padelcoreservice.service.contact.OrganizerContacts;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizerContactsTest {
    @ParameterizedTest
    @CsvSource({
            "'+54 9 11 2345-6789',AR,+5491123456789",
            "'011 15 2345-6789',AR,+5491123456789",
            "'(0351) 15 234-5678',AR,+5493512345678",
            "'0341 15 234-5678',AR,+5493412345678",
            "'5491123456789',AR,+5491123456789",
            "'+54 11 2345-6789',AR,+541123456789",
            "'+34 612 34 56 78',AR,+34612345678",
            "'612 34 56 78',ES,+34612345678",
            "'+7 (916) 123-45-67',AR,+79161234567",
            "'+44 20 7946 0018',GB,+442079460018"
    })
    void normalizesByCountryWithoutDuplicatingPrefixes(String input, String country, String expected) {
        var contact = OrganizerContacts.validate("QA Organizer", input, country);
        assertThat(contact.valid()).as(input).isTrue();
        assertThat(contact.name()).isEqualTo("QA Organizer");
        assertThat(contact.phone()).isEqualTo(expected);
        assertThat(contact.whatsappUrl()).isEqualTo("https://wa.me/" + expected.substring(1));
        assertThat(contact.needsReview()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://wa.me/5491123456789", "wa.me/5491123456789",
            "https://wa.me/5491123456789?text=Hola%20mundo",
            "https://api.whatsapp.com/send?phone=5491123456789&text=Hola",
            "https://api.whatsapp.com/send?phone=%2B5491123456789",
            "https://api.whatsapp.com/send/?phone=5491123456789&text&type=phone_number&app_absent=0",
            "https://api.whatsapp.com/send?phone=+5491123456789"})
    void extractsNumberAndRebuildsSupportedLink(String input) {
        var contact = OrganizerContacts.validate("QA", input, "AR");
        assertThat(contact.valid()).isTrue();
        assertThat(contact.phone()).isEqualTo("+5491123456789");
        assertThat(contact.whatsappUrl()).isEqualTo("https://wa.me/5491123456789");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://wa.me/message/SECRET", "https://evil.test/5491123456789",
            "https://wa.me.evil.test/5491123456789", "https://wa.me@evil.test/5491123456789",
            "https://api.whatsapp.com/send?phone=5491123456789&phone=5493512345678",
            "https://wa.me/5491123456789?phone=5493512345678",
            "http://wa.me/5491123456789", "https://wa.me/5491123456789/other",
            "https://wa.me/5491123456789#other"})
    void rejectsLinksThatDoNotIdentifyOneSupportedNumber(String input) {
        var contact = OrganizerContacts.validate("QA", input, "AR");
        assertThat(contact.valid()).isFalse();
        assertThat(contact.whatsappUrl()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "+54 9 9 11 2345-6789", "Name +5491123456789",
            "+5491123456789 +5493512345678", "@telegram", "qa@example.test"})
    void invalidInputNeverCreatesALink(String input) {
        var contact = OrganizerContacts.validate("QA", input, "AR");
        assertThat(contact.valid()).isFalse();
        assertThat(contact.needsReview()).isTrue();
        assertThat(contact.whatsappUrl()).isEmpty();
    }

    @Test void separatesLegacyNameWithoutMutatingStoredValue() {
        var event = Tournament.builder().contactoOrganizador("QA Organizer +54 9 11 2345-6789").build();
        var contact = OrganizerContacts.resolve(event);
        assertThat(contact.name()).isEqualTo("QA Organizer");
        assertThat(contact.phone()).isEqualTo("+5491123456789");
        assertThat(event.getContactoOrganizador()).isEqualTo("QA Organizer +54 9 11 2345-6789");
        assertThat(event.getOrganizerPhone()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Organizer 54 11", "Alice +5491123456789; Bob +5493512345678", "Call me later"})
    void ambiguousLegacyIsPreservedAndMarkedForReview(String raw) {
        var event = Tournament.builder().contactoOrganizador(raw).build();
        assertThat(OrganizerContacts.resolve(event).needsReview()).isTrue();
        assertThat(OrganizerContacts.resolve(event).whatsappUrl()).isEmpty();
        assertThat(event.getContactoOrganizador()).isEqualTo(raw);
    }

    @Test void structuredInputHasPriorityAndCannotFallBackAfterClearingPhone() {
        var dto = TournamentDto.builder().contactoOrganizador("+5491123456789")
                .organizerPhone("").organizerCountry("AR").build();
        assertThat(OrganizerContacts.input(dto).valid()).isFalse();
    }

    @Test void internationalPasteChangesCountryAndCountryListUsesMetadata() {
        assertThat(OrganizerContacts.validate("QA", "+34612345678", "AR").country()).isEqualTo("ES");
        var countries = OrganizerContacts.countries(Locale.forLanguageTag("ru"));
        assertThat(countries.get(0).code()).isEqualTo("AR");
        assertThat(countries.get(1).code()).isEqualTo("RU");
        assertThat(countries).extracting(OrganizerContacts.Country::code).contains("ES", "GB", "BR");
        assertThat(countries.get(0).example()).isNotBlank();
    }
}
