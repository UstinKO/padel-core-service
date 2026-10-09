package com.padle.core.padelcoreservice.service.americano;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.mapper.americano.AmericanoMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoPlayer;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * LFPT-0525: расчёт отдыхающих игроков раунда не пишет INFO-логов (метод вызывается
 * на каждый раунд при каждом просмотре страницы турнира) и даёт прежний результат.
 */
@ExtendWith(MockitoExtension.class)
class AmericanoServiceByePlayersTest {

    private static final long TOURNAMENT_ID = 1L;
    private static final long ROUND_ID = 10L;

    @Mock private AmericanoRoundRepository americanoRoundRepository;
    @Mock private AmericanoMatchRepository americanoMatchRepository;
    @Mock private AmericanoPlayerRepository americanoPlayerRepository;
    @Mock private AmericanoMapper americanoMapper;

    @InjectMocks private AmericanoService americanoService;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(AmericanoService.class);
    private Level originalLevel;

    private final List<PlayerPadel> players = LongStream.rangeClosed(1, 5)
            .mapToObj(id -> PlayerPadel.builder().id(id).nombre("Jugador" + id).apellido("Apellido").build())
            .toList();

    @BeforeEach
    void setUp() {
        originalLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.INFO);
        appender.start();
        serviceLogger.addAppender(appender);

        Tournament tournament = Tournament.builder().id(TOURNAMENT_ID).build();
        AmericanoRound round = AmericanoRound.builder().id(ROUND_ID).tournament(tournament).build();
        when(americanoRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(americanoMapper.toDto(any(AmericanoRound.class)))
                .thenAnswer(inv -> AmericanoRoundDto.builder().id(ROUND_ID).tournamentId(TOURNAMENT_ID).build());
        when(americanoPlayerRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, AmericanoPlayerStatus.ACTIVE))
                .thenReturn(players.stream().map(p -> AmericanoPlayer.builder().player(p).build()).toList());
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(appender);
        serviceLogger.setLevel(originalLevel);
    }

    @Test
    void playersOutsideMatches_areBye_withoutInfoLogs() {
        AmericanoMatch match = AmericanoMatch.builder()
                .team1Player1(players.get(0)).team1Player2(players.get(1))
                .team2Player1(players.get(2)).team2Player2(players.get(3))
                .build();
        when(americanoMatchRepository.findByRoundId(ROUND_ID)).thenReturn(List.of(match));

        AmericanoRoundDto dto = americanoService.getRound(ROUND_ID);

        assertThat(dto.getByePlayerNames()).containsExactly("Jugador5 Apellido");
        assertThat(appender.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.INFO));
    }

    @Test
    void emptySlotInMatch_isIgnored() {
        AmericanoMatch match = AmericanoMatch.builder()
                .team1Player1(players.get(0)).team1Player2(null)
                .team2Player1(players.get(2)).team2Player2(players.get(3))
                .build();
        when(americanoMatchRepository.findByRoundId(ROUND_ID)).thenReturn(List.of(match));

        AmericanoRoundDto dto = americanoService.getRound(ROUND_ID);

        assertThat(dto.getByePlayerNames()).containsExactly("Jugador2 Apellido", "Jugador5 Apellido");
    }

    @Test
    void roundWithoutMatches_allPlayersAreBye_withSingleWarning() {
        when(americanoMatchRepository.findByRoundId(ROUND_ID)).thenReturn(List.of());

        AmericanoRoundDto dto = americanoService.getRound(ROUND_ID);

        assertThat(dto.getByePlayerNames()).hasSize(players.size());
        assertThat(appender.list).singleElement()
                .satisfies(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
    }
}
