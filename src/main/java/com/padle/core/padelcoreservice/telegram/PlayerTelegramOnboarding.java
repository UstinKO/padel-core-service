package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PlayerTelegramOnboarding {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PlayerTelegramLinks links;
    private final PlayerTelegramStore connections;
    private final PlayerTelegramOnboardingStore requests;
    private final PlayerTelegramDelivery delivery;
    private final PlayerTelegramMessages messages;
    private final PlayerRepository players;
    private final PlayerTelegramAccess access;
    private final boolean enabled;
    private final String baseUrl;

    public record Review(String state, String playerName, String botUrl) {}
    public record Invitation(boolean available, String url) {}

    public PlayerTelegramOnboarding(PlayerTelegramLinks links, PlayerTelegramStore connections,
            PlayerTelegramOnboardingStore requests, PlayerTelegramDelivery delivery,
            PlayerTelegramMessages messages, PlayerRepository players, PlayerTelegramAccess access,
            @Value("${telegram.players.onboarding-enabled:false}") boolean enabled,
            @Value("${app.base-url}") String baseUrl) {
        this.links=links; this.connections=connections; this.requests=requests; this.delivery=delivery;
        this.messages=messages; this.players=players; this.enabled=enabled;
        this.access=access;
        this.baseUrl=baseUrl.replaceAll("/+$", "");
    }

    public boolean available() { return enabled && links.available() && !access.pilot(); }

    public Invitation invitation() {
        return new Invitation(available(), available() ? links.botUrl()+"?start=connect" : "");
    }

    public void start(long updateId, User user) {
        if (!available() || connections.deliveryResult("reply:"+updateId).isPresent()) return;
        var locale = language(user.languageCode());
        var connected = connections.byUser(user.id()).flatMap(c -> players.findById(c.playerId()))
                .filter(p -> p.isActivo() && !p.isGuestAccount());
        if (connected.isPresent()) {
            delivery.sendOnce("reply:"+updateId, null, null, user.id(),
                    messages.text("onboarding.connected", connected.orElseThrow().getLocale()), List.of());
            return;
        }
        byte[] bytes=new byte[24];
        RANDOM.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        requests.issue(UUID.randomUUID(), user.id(), PlayerTelegramLinks.hash(token),
                OffsetDateTime.now().plusMinutes(10), locale.getLanguage());
        delivery.sendOnce("reply:"+updateId, null, null, user.id(), messages.text("onboarding.welcome", locale),
                List.of(List.of(Button.link(messages.text("onboarding.open", locale),
                        baseUrl+"/telegram/connect#token="+token))));
    }

    public Review review(String token, PlayerPadel player) {
        var request = request(token);
        if (request.playerId()!=null && !request.playerId().equals(player.getId())) throw PlayerTelegramOnboardingStore.invalid();
        if (!links.status(player).eligible()) throw PlayerTelegramOnboardingStore.invalid();
        return new Review(request.completed()?"CONNECTED":request.playerId()==null?"READY":"PENDING",
                player.getNombreCompleto(), links.botUrl());
    }

    public Review stage(String token, PlayerPadel player, Locale locale) {
        request(token);
        var pending=requests.stage(PlayerTelegramLinks.hash(token), player.getId(), language(locale.getLanguage()).getLanguage());
        if (!pending.completed()) {
            var lang=language(pending.locale());
            delivery.sendOnce("onboarding:"+pending.id(), null, null, pending.chatId(),
                    messages.text("onboarding.confirm", lang, player.getNombreCompleto()),
                    List.of(List.of(Button.action(messages.text("onboarding.accept", lang), "l:"+pending.id()))));
        }
        return review(token, player);
    }

    public Optional<Long> complete(UUID id, long userId) {
        return available() ? requests.complete(id,userId) : Optional.empty();
    }

    public void revoke(long userId) { requests.revoke(userId); }

    public UUID requestId(String token) { return request(token).id(); }

    private PlayerTelegramOnboardingStore.Request request(String token) {
        if (!available() || token==null || !token.matches("[A-Za-z0-9_-]{32}")) throw PlayerTelegramOnboardingStore.invalid();
        return requests.review(PlayerTelegramLinks.hash(token)).orElseThrow(PlayerTelegramOnboardingStore::invalid);
    }

    static Locale language(String value) {
        String language=Locale.forLanguageTag(value==null?"es":value).getLanguage();
        return Locale.forLanguageTag(Set.of("es","ru","en").contains(language)?language:"es");
    }
}
