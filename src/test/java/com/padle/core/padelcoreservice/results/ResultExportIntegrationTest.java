package com.padle.core.padelcoreservice.results;

import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.mail.username=test@example.test","spring.mail.password=test","recaptcha.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test","spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test","recaptcha.secret-key=test","app.scheduling.enabled=false","telegram.enabled=false",
        "telegram.players.automatic-enabled=false","app.analytics.meta.enabled=false","app.auth.google-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ResultExportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ResultExportFixtures fixtures;
    @Autowired ResultSnapshot snapshots;
    @Autowired ResultFiles files;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest @ValueSource(strings={"OWNER","ADMIN","ORGANIZER","CLUB_ADMIN","PLAYER"})
    void refusesEveryOtherRoleEvenForKnownId(String role) throws Exception {
        var sample=fixtures.sample("OPEN",fixtures.account(OwnerRole.SUPER_ADMIN,"Qa-only-1502"));
        long id=sample.event().getId();
        mvc.perform(get(url(id)+"/preview").with(user("qa").roles(role))).andExpect(redirectedUrl("/error/403"));
        mvc.perform(get("/admin/export").with(user("qa").roles(role))).andExpect(redirectedUrl("/error/403"));
        for(var format:List.of("XLSX","CSV")) mvc.perform(post(url(id)).with(csrf()).with(user("qa").roles(role))
                .param("format",format).param("contacts","true").param("acknowledged","true"))
                .andExpect(redirectedUrl("/error/403")).andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test void requiresLoginCsrfAndExplicitContactAcknowledgement() throws Exception {
        mvc.perform(get(url(1)+"/preview")).andExpect(redirectedUrl("/login"));
        mvc.perform(post(url(1)).with(user("qa").roles("SUPER_ADMIN"))).andExpect(redirectedUrl("/error/403"));
        mvc.perform(post(url(1)).with(csrf()).with(user("qa").roles("SUPER_ADMIN")).param("contacts","true"))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Content-Disposition"));
        mvc.perform(post(url(1)).with(csrf()).with(user("qa").roles("SUPER_ADMIN")).param("format","PDF"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings={"AMERICANO","KING","PLAYOFF","POINTS","SETS","BRACKET","OPEN","CLINIC"})
    void exportsAllFormatsAndOrganizersWithoutChangingSportData(String kind) throws Exception {
        var sample=fixtures.sample(kind,fixtures.account("SETS".equals(kind)?OwnerRole.CLUB_ADMIN:OwnerRole.ORGANIZER,"Qa-only-1502"));
        long id=sample.event().getId();
        var before=fingerprint(id);
        var report=snapshots.read(id,false);
        assertThat(report.participants()).hasSize(6);
        assertThat(report.participants()).extracting(p->p.person().id()).doesNotHaveDuplicates();
        assertThat(report.participants()).allMatch(p->p.person().phone()==null);
        byte[] body=mvc.perform(post(url(id)).with(csrf()).with(user("export-qa").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andReturn().getResponse().getContentAsByteArray();
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(body))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("Tournament");
            assertThat(workbook.getSheet("Participants").getLastRowNum()).isEqualTo(6);
            assertThat(headers(workbook.getSheet("Participants"))).doesNotContain("phone");
            assertThat(workbook.getSheet("Matches").getLastRowNum()).isEqualTo(report.matches().size());
            var all=workbook.sheetIterator();var text=new StringBuilder();
            while(all.hasNext()) for(var row:all.next()) for(var cell:row) text.append(cell).append('|');
            assertThat(text.toString()).doesNotContain("549115550000","private_qa","private-test-hash","@example.test");
        }
        var zip=mvc.perform(post(url(id)).with(csrf()).with(user("export-qa").roles("SUPER_ADMIN")).param("format","CSV"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var stream=new ZipInputStream(new ByteArrayInputStream(zip),StandardCharsets.UTF_8)) {
            for(var name:List.of("Tournament.csv","Participants.csv","Matches.csv")) {
                assertThat(stream.getNextEntry().getName()).isEqualTo(name);
                var text=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                assertThat(text).startsWith("\ufeff").doesNotContain("549115550000","private_qa","private-test-hash","@example.test");
            }
            assertThat(stream.getNextEntry()).isNull();
        }
        assertThat(fingerprint(id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tournament_result_exports WHERE tournament_id=?",Integer.class,id)).isEqualTo(2);
        if("SETS".equals(kind)) {
            var winner=report.participants().stream().filter(p->java.util.Objects.equals(p.person().id(),sample.players().get(2).getId())).findFirst().orElseThrow();
            assertThat(winner.finalPlace()).isEqualTo(1);
            var output=Path.of("target/results-export-qa/1padel-results-example.xlsx");Files.createDirectories(output.getParent());Files.write(output,body);
        }
        if("AMERICANO".equals(kind)) {
            var first=report.participants().get(0);assertThat(first.standing().adjustment()).isEqualTo(3L);
            assertThat(first.standing().position()).isEqualTo(6);assertThat(first.standing().matchPoints()).isEqualTo(18L);
        }
        if("KING".equals(kind)) {
            assertThat(report.matches().get(0).team1Players()).contains(sample.players().get(2).getId().toString());
            var first=report.participants().get(0);assertThat(first.standing().courtBonus()).isEqualTo(3L);
            assertThat(first.standing().matchPoints()).isEqualTo(18L);
        }
        if("PLAYOFF".equals(kind)) {
            assertThat(report.participants()).filteredOn(p->p.finalPlace()!=null).hasSize(4);
            assertThat(report.participants().get(4).finalPlace()).isNull();
        }
        if("BRACKET".equals(kind)) assertThat(report.matches().get(0).technicalResult()).isEqualTo("WALKOVER");
        if(List.of("OPEN","CLINIC").contains(kind)) assertThat(report.warnings()).contains("NO_MATCHES","NO_STANDINGS");
    }

    @Test void contactsAreOptInAndInvalidTelegramInPhoneIsNotExported() throws Exception {
        var s=fixtures.sample("OPEN",fixtures.account(OwnerRole.SUPER_ADMIN,"Qa-only-1502"));long id=s.event().getId();
        jdbc.update("UPDATE player_padel_db SET telefono='@LeadgenMe' WHERE id=?",s.players().get(0).getId());
        var report=snapshots.read(id,true);assertThat(report.warnings()).contains("INVALID_PHONE_EXCLUDED","CONTACTS_INCLUDED");
        var body=mvc.perform(post(url(id)).with(csrf()).with(user("qa").roles("SUPER_ADMIN")).param("contacts","true").param("acknowledged","true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var wb=WorkbookFactory.create(new ByteArrayInputStream(body))) {
            var sheet=wb.getSheet("Participants");assertThat(headers(sheet)).contains("phone");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("+5491155500001");
        }
        assertThat(jdbc.queryForObject("SELECT contacts FROM tournament_result_exports WHERE tournament_id=?",Boolean.class,id)).isTrue();
    }

    @Test void preliminaryResultsNeverBecomeFinalPlacesAndCancelledMatchesHaveNoWinner() {
        var s=fixtures.sample("AMERICANO",fixtures.account(OwnerRole.SUPER_ADMIN,"Qa-only-1502"));long id=s.event().getId();
        jdbc.update("UPDATE tournaments_db SET estado='EN_CURSO' WHERE id=?",id);
        jdbc.update("UPDATE americano_matches_db SET status='CANCELLED' WHERE tournament_id=?",id);
        var report=snapshots.read(id,false);assertThat(report.preliminary()).isTrue();assertThat(report.warnings()).contains("PROVISIONAL");
        assertThat(report.participants()).allMatch(p->p.finalPlace()==null);
        assertThat(report.matches()).allMatch(g->g.winnerPlayers().isEmpty());
    }

    @Test void retainsUnlinkedGuestsWithoutInventingAProfileId() {
        var s=fixtures.sample("PLAYOFF",fixtures.account(OwnerRole.SUPER_ADMIN,"Qa-only-1502"));long id=s.event().getId();
        long second=s.players().get(1).getId();
        jdbc.update("UPDATE americano_teams_db SET player2_id=null,player2_name='Invitado sin perfil',player2_phone='+5491155500888' WHERE tournament_id=? AND player2_id=?",id,second);
        jdbc.update("UPDATE americano_matches_db SET team1_player2_id=null WHERE tournament_id=? AND team1_player2_id=?",id,second);
        jdbc.update("UPDATE americano_matches_db SET team2_player2_id=null WHERE tournament_id=? AND team2_player2_id=?",id,second);
        var report=snapshots.read(id,true);
        assertThat(report.warnings()).contains("LEGACY_GUEST_WITHOUT_PROFILE");
        assertThat(report.participants()).filteredOn(p->p.person().id()==null).singleElement()
                .satisfies(p->{assertThat(p.person().firstName()).isEqualTo("Invitado sin perfil");assertThat(p.person().phone()).isEqualTo("+5491155500888");});
        assertThat(report.matches()).anyMatch(g->g.team1Names().contains("Invitado sin perfil")||g.team2Names().contains("Invitado sin perfil"));
    }

    @Test void usesOnlyCurrentKingRunAfterRestartAndKeepsMissingScoresBlank() {
        var s=fixtures.sample("KING",fixtures.account(OwnerRole.SUPER_ADMIN,"Qa-only-1502"));long id=s.event().getId();
        long current=jdbc.queryForObject("SELECT id FROM tournament_king_of_court_db WHERE tournament_id=?",Long.class,id);
        jdbc.update("INSERT INTO tournament_king_of_court_db (tournament_id,max_courts,calibration_rounds,current_round,is_active,is_finished) VALUES (?,1,1,0,true,false)",id);
        var report=snapshots.read(id,false);
        assertThat(report.matches()).isEmpty();assertThat(report.warnings()).contains("NO_MATCHES");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM king_of_court_match_result_db m JOIN king_of_court_court_db c ON c.id=m.court_id JOIN king_of_court_round_db r ON r.id=c.round_id WHERE r.tournament_king_id=?",Integer.class,current)).isEqualTo(1);
    }

    @Test void classicPointTotalsAreNotMislabeledAsSets() {
        var s=fixtures.sample("BRACKET",fixtures.account(OwnerRole.SUPER_ADMIN,"Qa-only-1502"));long id=s.event().getId();
        jdbc.update("UPDATE matches_db SET estado='FINALIZADO',puntos_equipo1=20,puntos_equipo2=12,set1_equipo1=6,set1_equipo2=4 WHERE tournament_id=?",id);
        var game=snapshots.read(id,false).matches().get(0);
        assertThat(game.scoreUnit()).isEqualTo("POINTS");assertThat(game.score1()).isEqualTo(20);
        assertThat(game.sets()).isEqualTo("6:4");
    }

    private String fingerprint(long id) { return jdbc.queryForObject("""
            SELECT jsonb_build_object('event',(SELECT to_jsonb(t) FROM tournaments_db t WHERE id=?),
              'matches',(SELECT jsonb_agg(to_jsonb(m) ORDER BY id) FROM americano_matches_db m WHERE tournament_id=?),
              'registrations',(SELECT jsonb_agg(to_jsonb(r) ORDER BY id) FROM tournament_registrations_db r WHERE tournament_id=?))::text
            """,String.class,id,id,id); }
    private String url(long id) { return "/admin/tournaments/"+id+"/results-export"; }
    private List<String> headers(org.apache.poi.ss.usermodel.Sheet sheet) {
        return java.util.stream.StreamSupport.stream(sheet.getRow(0).spliterator(),false).map(Object::toString).toList();
    }
}
