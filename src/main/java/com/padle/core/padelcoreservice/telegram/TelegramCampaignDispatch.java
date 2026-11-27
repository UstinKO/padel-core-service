package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.telegram.CampaignModels.*;
import com.padle.core.padelcoreservice.telegram.TelegramModels.Result;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TelegramCampaignDispatch {
    private final TelegramCampaignStore store;
    private final PlayerTelegramStore connections;
    private final CampaignAudienceGuard guard;
    private final PlayerTelegramDelivery delivery;

    @Transactional
    public void send(Dispatch dispatch) {
        connections.guardConnections();
        var header = store.find(dispatch.campaignId()).orElseThrow();
        var row = store.rows(header.id()).stream().filter(r -> r.playerId()==dispatch.playerId()).findFirst().orElseThrow();
        if (!row.state().equals("IN_FLIGHT") || row.attempt()!=dispatch.attempt()) return;
        var reason = guard.reason(header,row);
        if (!guard.enabled() || reason.isPresent()) {
            store.result(header.id(),row.playerId(),"SKIPPED",reason.orElse("DISABLED"));
        } else {
            delivery.sendOnce(dispatch.key(),header.tournamentId(),null,row.chatId(),row.text().isEmpty() ? header.text() : row.text(),List.of());
            var result = connections.deliveryResult(dispatch.key()).orElseGet(() -> new Result("FAILED",null));
            store.result(header.id(),row.playerId(),result.state(),result.state().equals("FAILED") ? "PROVIDER_REJECTED" : result.state());
        }
        store.finishCampaigns();
    }
}
