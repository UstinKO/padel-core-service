package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.security.TelegramOnboardingSession;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramOnboarding;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TelegramConnectController {
    private final PlayerTelegramOnboarding onboarding;
    private final AccountService accounts;
    private final MessageSource messages;

    @GetMapping("/telegram/connect")
    public String connect(@RequestParam(defaultValue="") String request, Authentication auth, HttpSession session,
                          Model model, RedirectAttributes redirect, HttpServletResponse response, Locale locale) {
        protect(response);
        String token=TelegramOnboardingSession.token(session,request);
        model.addAttribute("requestId",token.isEmpty()?"":request);
        if (onboarding.available() && !token.isEmpty() &&
                (auth==null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)) {
            redirect.addAttribute("destination",TelegramOnboardingSession.destination(UUID.fromString(request)));
            return "redirect:/login";
        }
        try {
            model.addAttribute("link",!onboarding.available() || (!request.isEmpty() && token.isEmpty())
                    ? new PlayerTelegramOnboarding.Review("INVALID","","") : token.isEmpty()
                    ? new PlayerTelegramOnboarding.Review("ENTRY","","")
                    : onboarding.review(token,accounts.player(auth)));
        } catch (AccountException exception) {
            model.addAttribute("link",new PlayerTelegramOnboarding.Review("INVALID","",onboarding.invitation().url()));
        }
        if (new ClassPathResource("templates/telegram-connect.html").exists()) return "telegram-connect";
        model.addAttribute("type","warning");
        model.addAttribute("message",messages.getMessage("account.TELEGRAM_LINK_INVALID",null,locale));
        return "waitlist-confirmation";
    }

    public record Token(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{32}") String token) {}
    public record Continue(String next) {}

    @PostMapping("/telegram/connect/context")
    @ResponseBody
    public Continue remember(@Valid @RequestBody Token token, HttpSession session, HttpServletResponse response) {
        protect(response);
        UUID id=onboarding.requestId(token.token());
        TelegramOnboardingSession.remember(session,id,token.token());
        return new Continue(TelegramOnboardingSession.destination(id));
    }

    private static void protect(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-transform");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy",
                "script-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
    }
}
