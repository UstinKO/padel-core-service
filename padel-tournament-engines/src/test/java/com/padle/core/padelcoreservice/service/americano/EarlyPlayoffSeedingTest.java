package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.service.americano.EarlyPlayoffSeeding.Fixture;
import com.padle.core.padelcoreservice.service.americano.EarlyPlayoffSeeding.Pair;
import com.padle.core.padelcoreservice.service.americano.EarlyPlayoffSeeding.Result;
import com.padle.core.padelcoreservice.service.americano.EarlyPlayoffSeeding.Standing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EarlyPlayoffSeedingTest {

    private final EarlyPlayoffSeeding seeding = new EarlyPlayoffSeeding(new PlayoffMatchingEngine());

    @ParameterizedTest
    @CsvSource({"6,0", "6,1", "6,2", "6,3", "6,4", "6,5",
            "0,6", "1,6", "2,6", "3,6", "4,6", "5,6"})
    void acceptsEveryPermittedQualificationScore(int first, int second) {
        assertThat(EarlyPlayoffSeeding.validScore(first, second)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"-1,6", "6,-1", "6,6", "5,4", "7,0", "0,7", "0,0"})
    void rejectsScoresOutsideQualificationRules(int first, int second) {
        assertThat(EarlyPlayoffSeeding.validScore(first, second)).isFalse();
    }

    @Test
    void recognizesBothEarlyRoundStatesOnly() {
        assertThat(EarlyPlayoffSeeding.isEarlyRoundNote("EARLY_ROUND_OF_16")).isTrue();
        assertThat(EarlyPlayoffSeeding.isEarlyRoundNote("CONFIRMED_ROUND_OF_16")).isTrue();
        assertThat(EarlyPlayoffSeeding.isEarlyRoundNote("ROUND_OF_16")).isFalse();
        assertThat(EarlyPlayoffSeeding.isEarlyRoundNote(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"8,8,0", "9,7,1", "10,6,2", "11,5,3", "12,4,4",
            "13,3,5", "14,2,6", "15,1,7", "16,0,8"})
    void excludesDirectQuarterfinalistsFromFirstStage(int count, int direct, int matches) {
        var standings = standings(count);
        var pairs = seeding.roundOf16(standings, Map.of(), Set.of());

        assertThat(EarlyPlayoffSeeding.projectedByes(standings))
                .containsExactlyElementsOf(LongStream.rangeClosed(1, direct).boxed().toList());
        assertThat(pairs).hasSize(matches);
        assertThat(pairs.stream().flatMap(pair -> Stream.of(pair.team1Id(), pair.team2Id())).toList())
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(direct + 1, count).boxed().toList());
    }

    @Test
    void projectedByesUseWinsDifferenceScoredGamesAndIdInThatOrder() {
        var standings = List.of(new Standing(9L, 0, 0, 12), new Standing(8L, 0, 0, 12),
                new Standing(7L, 0, 12, 1), new Standing(6L, 1, 9, 1), new Standing(5L, 1, 9, 1),
                new Standing(4L, 1, 10, 0), new Standing(3L, 2, 12, 2),
                new Standing(2L, 2, 10, 0), new Standing(1L, 2, 12, 0));

        assertThat(EarlyPlayoffSeeding.projectedByes(standings)).containsExactly(1L, 3L, 2L, 4L, 5L, 6L, 7L);
        assertThat(seeding.roundOf16(standings, Map.of(), Set.of()))
                .containsExactly(new PlayoffMatchingEngine.Pairing(8L, 9L));
    }

    @Test
    void preservesAnAlreadyConfirmedNonMirrorPairWithoutLosingOtherTeams() {
        var fixed = Set.of(Pair.of(5, 6));
        var pairs = seeding.roundOf16(standings(12), Map.of(), fixed);

        assertThat(pairs).contains(new PlayoffMatchingEngine.Pairing(5L, 6L)).hasSize(4);
        assertThat(pairs.stream().flatMap(pair -> Stream.of(pair.team1Id(), pair.team2Id())).toList())
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(5, 12).boxed().toList());
        assertThat(fixed).containsExactly(Pair.of(5, 6));
    }

    @Test
    void avoidsPreviousOpponentWhenAnAlternativePairingExists() {
        var pairs = seeding.roundOf16(standings(10), Map.of(7L, Set.of(10L), 10L, Set.of(7L)), Set.of());

        assertThat(pairs.stream().map(Pair::of).toList()).doesNotContain(Pair.of(7, 10)).hasSize(2);
    }

    @Test
    void emptyEligibilityProducesNoRecommendations() {
        assertThat(seeding.evaluate(standings(10), List.of(new Fixture(1, 1, 2)), Map.of(),
                Set.of(), null, Set.of())).isEqualTo(new Result(Set.of(), true, false, false));
    }

    @Test
    void completedQualificationKeepsOnlyEligibleSeededPairs() {
        var eligible = Set.of(Pair.of(7, 10), Pair.of(7, 8));

        var result = seeding.evaluate(standings(10), List.of(), Map.of(), eligible, null, Set.of());

        assertThat(result).isEqualTo(new Result(Set.of(Pair.of(7, 10)), true, false, false));
        assertThat(eligible).containsExactlyInAnyOrder(Pair.of(7, 10), Pair.of(7, 8));
    }

    @Test
    void stopsWhenNoStableRecommendationOrManualChoiceRemains() {
        var result = seeding.evaluate(standings(10), List.of(), Map.of(),
                Set.of(Pair.of(7, 8)), null, Set.of());

        assertThat(result).isEqualTo(new Result(Set.of(), false, false, false));
    }

    @Test
    void unfinishedGamesAmongDirectQuarterfinalistsKeepStablePlayInPairs() {
        var standings = LongStream.rangeClosed(1, 10)
                .mapToObj(id -> new Standing(id, id <= 6 ? 2 : 0, id <= 6 ? 12 : 0, 0)).toList();
        var eligible = Set.of(Pair.of(7, 10), Pair.of(8, 9));

        var result = seeding.evaluate(standings, List.of(new Fixture(1, 1, 2)), Map.of(),
                eligible, null, Set.of());

        assertThat(result).isEqualTo(new Result(eligible, true, false, false));
    }

    @Test
    void aPossibleDirectPassMakesTheManualPairUnavailable() {
        var standings = standings(9);
        var manual = Pair.of(8, 9);

        var result = seeding.evaluate(standings, List.of(new Fixture(1, 1, 9)), Map.of(),
                Set.of(manual), manual, Set.of());

        assertThat(result).isEqualTo(new Result(Set.of(), false, false, true));
        assertThat(standings).containsExactlyElementsOf(standings(9));
    }

    @Test
    void checksEveryScoreForAManualPairThatCannotReceiveABye() {
        var result = seeding.evaluate(standings(16), List.of(new Fixture(1, 1, 2)), Map.of(),
                Set.of(Pair.of(1, 3)), Pair.of(1, 3), Set.of());

        assertThat(result).isEqualTo(new Result(Set.of(), true, false, false));
    }

    @Test
    void belowScenarioLimitKeepsAFixedPairAcrossAllPossibleScores() {
        var pair = Pair.of(15, 16);

        var result = seeding.evaluate(standings(16), fixtures(3), Map.of(), Set.of(pair), null, Set.of(pair));

        assertThat(result).isEqualTo(new Result(Set.of(pair), true, false, false));
    }

    @Test
    void aboveScenarioLimitWithholdsEvenAPreviouslyStablePairAndKeepsInputs() {
        var standings = standings(16);
        var fixtures = fixtures(4);
        var pair = Pair.of(15, 16);
        var eligible = Set.of(pair);
        var fixed = Set.of(pair);

        var result = seeding.evaluate(standings, fixtures, Map.of(), eligible, null, fixed);

        assertThat(result).isEqualTo(new Result(Set.of(), false, true, false));
        assertThat(standings).containsExactlyElementsOf(standings(16));
        assertThat(fixtures).containsExactlyElementsOf(fixtures(4));
        assertThat(eligible).containsExactly(pair);
        assertThat(fixed).containsExactly(pair);
    }

    private List<Standing> standings(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> new Standing(id, 0, 0, 0)).toList();
    }

    private List<Fixture> fixtures(int count) {
        return IntStream.range(0, count).mapToObj(index -> new Fixture(index + 1, 2L * index + 1, 2L * index + 2))
                .toList();
    }
}
