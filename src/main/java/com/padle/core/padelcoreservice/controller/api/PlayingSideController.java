package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.PlayingSideDtos;
import com.padle.core.padelcoreservice.service.PlayingSideService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players/me/playing-side")
@RequiredArgsConstructor
public class PlayingSideController {
    private final PlayingSideService service;

    @ModelAttribute
    public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @PostMapping("/visit")
    public PlayingSideDtos.State visit(Authentication authentication) { return service.visit(authentication); }

    @PatchMapping
    public PlayingSideDtos.State answer(Authentication authentication, @Valid @RequestBody PlayingSideDtos.Answer answer) {
        return service.answer(authentication, answer);
    }

    @PostMapping("/defer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void defer(Authentication authentication) { service.defer(authentication); }
}
