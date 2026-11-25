package com.padle.core.padelcoreservice.preview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("preview")
@ConditionalOnProperty("app.preview.elo-history")
@RequestMapping("/api/internal/ui-preview/elo")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class PreviewEloHistoryController {
    private final PreviewEloHistory history;

    public record ScoreCorrection(@NotBlank @Size(max = 255) String matchKey,
                                  @NotNull @PositiveOrZero Integer left, @NotNull @PositiveOrZero Integer right,
                                  @NotBlank @Size(max = 1000) String reason) { }

    @PutMapping("/historical-score")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void correctScore(@Valid @RequestBody ScoreCorrection correction) {
        history.correctScore(correction.matchKey(), correction.left(), correction.right(), correction.reason());
    }
}
