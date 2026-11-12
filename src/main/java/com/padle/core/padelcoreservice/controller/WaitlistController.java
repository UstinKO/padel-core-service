package com.padle.core.padelcoreservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequiredArgsConstructor
public class WaitlistController {

    private final MessageSource messages;

    @RequestMapping(path = "/waitlist/confirm", method = {RequestMethod.GET, RequestMethod.POST})
    public String showRetiredInvitation(Model model) {
        model.addAttribute("message", messages.getMessage("registration.reserve.old_link", null,
                LocaleContextHolder.getLocale()));
        model.addAttribute("type", "warning");
        return "waitlist-confirmation";
    }
}
