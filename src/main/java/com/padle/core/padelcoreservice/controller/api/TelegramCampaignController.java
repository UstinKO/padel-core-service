package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.telegram.TelegramCampaignService;
import com.padle.core.padelcoreservice.telegram.CampaignModels.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/telegram/messages/campaigns")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ORGANIZER','ADMIN','CLUB_ADMIN')")
public class TelegramCampaignController {
    private final TelegramCampaignService campaigns;
    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
    @PostMapping public Prepared prepare(@AuthenticationPrincipal Owner owner,@Valid @RequestBody Prepare prepare) {
        return campaigns.prepare(owner,prepare);
    }
    @GetMapping public List<View> history(@AuthenticationPrincipal Owner owner,@RequestParam(required=false) Long tournamentId,
                                          @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page) {
        return campaigns.history(owner,tournamentId,page);
    }
    @GetMapping("/{id}") public View get(@AuthenticationPrincipal Owner owner,@PathVariable UUID id) { return campaigns.get(owner,id); }
    @PostMapping("/{id}/confirm") public View confirm(@AuthenticationPrincipal Owner owner,@PathVariable UUID id,
                                                      @Valid @RequestBody Confirm confirm) { return campaigns.confirm(owner,id); }
    @PostMapping("/{id}/test") public View test(@AuthenticationPrincipal Owner owner,@PathVariable UUID id,
                                                @Valid @RequestBody Action action) { return campaigns.test(owner,id,action); }
    @PostMapping("/{id}/retry") public View retry(@AuthenticationPrincipal Owner owner,@PathVariable UUID id,
                                                  @Valid @RequestBody Action action) { return campaigns.retry(owner,id,action); }
}
