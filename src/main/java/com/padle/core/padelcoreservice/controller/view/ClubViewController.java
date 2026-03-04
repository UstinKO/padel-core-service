package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.service.ClubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ClubViewController {

    private final ClubService clubService;

    @GetMapping
    public String listClubs(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null &&
                authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser");

        model.addAttribute("isAuthenticated", isAuthenticated);

        List<ClubDto> clubs = clubService.getActiveClubs();
        model.addAttribute("clubs", clubs);

        return "clubs/list";
    }

    @GetMapping("/{id}")
    public String viewClub(@PathVariable Long id, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null &&
                authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser");

        model.addAttribute("isAuthenticated", isAuthenticated);

        return clubService.getClubById(id)
                .map(club -> {
                    model.addAttribute("club", club);
                    return "clubs/view";
                })
                .orElse("redirect:/clubs");
    }
}