package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.model.enums.Nivel;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;

public final class TelegramAudienceModels {
    private TelegramAudienceModels() {}
    public enum Group { ALL, MAIN, RESERVE, UNPAIRED, PENDING }
    public enum Availability { AVAILABLE, NOT_CONNECTED, WHATSAPP, GUEST, INACTIVE, OUTSIDE_PILOT, DISABLED }
    public record Candidate(long playerId, String name, Nivel category, Set<Group> groups, Availability availability) {}
    public record Audience(Long tournamentId, String name, boolean globalAccess, List<Candidate> candidates) {}
    public record Selection(Long tournamentId,
            @NotNull Set<@NotNull Group> groups,
            @NotNull Set<@NotNull Nivel> categories,
            @NotNull @Size(max = 10000) Set<@NotNull @Positive Long> includedPlayerIds,
            @NotNull @Size(max = 10000) Set<@NotNull @Positive Long> excludedPlayerIds,
            @NotBlank @Size(max = 4096) String text) {}
    public record Preview(String text, List<Candidate> recipients, List<Candidate> unavailable,
                          int selectedCount, boolean sendingAvailable) {}
}
