package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.annotation.Counted;
import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.DuplicateGroupDto;
import com.padle.core.padelcoreservice.dto.DuplicateMatchType;
import com.padle.core.padelcoreservice.dto.DuplicatesAuditReportDto;
import com.padle.core.padelcoreservice.dto.DuplicatesAuditSummaryDto;
import com.padle.core.padelcoreservice.dto.PlayerSummaryDto;
import com.padle.core.padelcoreservice.mapper.PlayerSummaryMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LFPT-0677: только аудит потенциальных дублей {@link PlayerPadel} — ничего не меняет
 * и не удаляет, см. спеку {@code specs/features/LFPT-0677-аудит-дублей-игроков.md}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerDuplicatesAuditService {

    private final PlayerRepository playerRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final PlayerSummaryMapper playerSummaryMapper;

    private static final int NATIONAL_PHONE_DIGITS = 10;
    private static final int MIN_COMPARABLE_PHONE_DIGITS = 6;

    @Timed(
            name = "admin.players.duplicates.audit.time",
            description = "Time to compute player duplicates audit report",
            tags = {"service=playerDuplicatesAudit"}
    )
    @Counted(
            name = "admin.players.duplicates.audit.access",
            description = "Player duplicates audit report access count"
    )
    @Transactional(readOnly = true)
    public DuplicatesAuditReportDto buildReport() {
        List<PlayerPadel> players = playerRepository.findAll();
        Set<Long> partnerInviteCreatedIds = Set.copyOf(registrationRepository.findPartnerInviteCreatedPlayerIds());

        List<DuplicateGroupDto> exactDuplicates = Stream.concat(
                        groupBy(players, this::normalizedPhone, DuplicateMatchType.PHONE).stream(),
                        groupBy(players, this::normalizedTelegram, DuplicateMatchType.TELEGRAM).stream())
                .toList();

        List<DuplicateGroupDto> probableDuplicates = groupBy(players, this::normalizedName, DuplicateMatchType.NAME)
                .stream()
                .filter(nameGroup -> !fullyCoveredByAnyGroup(nameGroup, exactDuplicates))
                .toList();

        List<PlayerSummaryDto> partnerFlowSuspects = players.stream()
                .filter(p -> partnerInviteCreatedIds.contains(p.getId()))
                .map(playerSummaryMapper::toSummary)
                .toList();

        Map<String, Long> sourceBreakdown = players.stream()
                .collect(Collectors.groupingBy(p -> classifySource(p, partnerInviteCreatedIds), Collectors.counting()));

        DuplicatesAuditSummaryDto summary = buildSummary(players.size(), exactDuplicates, probableDuplicates);

        return new DuplicatesAuditReportDto(summary, exactDuplicates, probableDuplicates, partnerFlowSuspects, sourceBreakdown);
    }

    private List<DuplicateGroupDto> groupBy(
            List<PlayerPadel> players,
            Function<PlayerPadel, Optional<String>> keyExtractor,
            DuplicateMatchType matchType) {
        Map<String, List<PlayerPadel>> byKey = players.stream()
                .filter(p -> keyExtractor.apply(p).isPresent())
                .collect(Collectors.groupingBy(p -> keyExtractor.apply(p).orElseThrow()));

        return byKey.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> new DuplicateGroupDto(
                        matchType,
                        entry.getKey(),
                        entry.getValue().stream().map(playerSummaryMapper::toSummary).toList()))
                .toList();
    }

    private boolean fullyCoveredByAnyGroup(DuplicateGroupDto group, List<DuplicateGroupDto> otherGroups) {
        Set<Long> memberIds = group.members().stream().map(PlayerSummaryDto::id).collect(Collectors.toSet());
        return otherGroups.stream()
                .map(other -> other.members().stream().map(PlayerSummaryDto::id).collect(Collectors.toSet()))
                .anyMatch(otherIds -> otherIds.containsAll(memberIds));
    }

    private Optional<String> normalizedPhone(PlayerPadel player) {
        String raw = player.getTelefono();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < MIN_COMPARABLE_PHONE_DIGITS) {
            return Optional.empty();
        }
        String normalized = digits.length() > NATIONAL_PHONE_DIGITS
                ? digits.substring(digits.length() - NATIONAL_PHONE_DIGITS)
                : digits;
        return Optional.of(normalized);
    }

    private Optional<String> normalizedTelegram(PlayerPadel player) {
        String raw = player.getTelegramUsername();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.trim().toLowerCase(Locale.ROOT));
    }

    private Optional<String> normalizedName(PlayerPadel player) {
        String nombre = player.getNombre() == null ? "" : player.getNombre();
        String apellido = player.getApellido() == null ? "" : player.getApellido();
        String combined = (nombre + " " + apellido).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return combined.isBlank() ? Optional.empty() : Optional.of(combined);
    }

    private String classifySource(PlayerPadel player, Set<Long> partnerInviteCreatedIds) {
        if (player.isGuestAccount()) {
            return "ADMIN_GUEST";
        }
        if (player.isOauth2User()) {
            return "OAUTH2";
        }
        if (partnerInviteCreatedIds.contains(player.getId())) {
            return "PARTNER_INVITE";
        }
        return "SELF_REGISTERED";
    }

    private DuplicatesAuditSummaryDto buildSummary(
            int totalRecords,
            List<DuplicateGroupDto> exactDuplicates,
            List<DuplicateGroupDto> probableDuplicates) {
        UnionFind unionFind = new UnionFind();
        Stream.concat(exactDuplicates.stream(), probableDuplicates.stream())
                .forEach(group -> unionFind.unionAll(group.members().stream().map(PlayerSummaryDto::id).toList()));

        Collection<List<Long>> components = unionFind.components();
        long duplicateGroupsCount = components.size();
        long extraRecords = components.stream().mapToLong(members -> members.size() - 1L).sum();
        long uniquePlayersEstimate = totalRecords - extraRecords;

        Map<String, Long> distribution = components.stream()
                .collect(Collectors.groupingBy(
                        members -> members.size() >= 4 ? "4+" : String.valueOf(members.size()),
                        Collectors.counting()));

        return new DuplicatesAuditSummaryDto(totalRecords, duplicateGroupsCount, uniquePlayersEstimate, distribution);
    }

    /**
     * Простой union-find по id игроков — объединяет записи, попавшие в одну и ту же группу
     * (точную или вероятную), чтобы посчитать реальное число затронутых "людей", а не сумму
     * размеров пересекающихся групп (см. Edge cases спеки LFPT-0677).
     */
    private static final class UnionFind {
        private final Map<Long, Long> parent = new HashMap<>();

        long find(long id) {
            parent.putIfAbsent(id, id);
            long root = id;
            while (parent.get(root) != root) {
                root = parent.get(root);
            }
            parent.put(id, root);
            return root;
        }

        void unionAll(List<Long> ids) {
            if (ids.isEmpty()) {
                return;
            }
            long first = find(ids.get(0));
            for (Long id : ids) {
                parent.put(find(id), first);
            }
        }

        Collection<List<Long>> components() {
            return parent.keySet().stream()
                    .collect(Collectors.groupingBy(this::find))
                    .values();
        }
    }
}
