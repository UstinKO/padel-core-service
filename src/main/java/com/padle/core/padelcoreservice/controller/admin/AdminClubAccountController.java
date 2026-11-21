package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.ClubAccountService;
import com.padle.core.padelcoreservice.service.ClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Controller
@RequestMapping("/admin/club-accounts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminClubAccountController {
    private final ClubAccountService accounts;
    private final ClubService clubs;

    @GetMapping
    public String list(@AuthenticationPrincipal Owner actor, Model model) {
        model.addAttribute("accounts", accounts.accounts(actor));
        return "admin/club-accounts/list";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, @AuthenticationPrincipal Owner actor, Model model) {
        Owner account = accounts.account(actor, id);
        model.addAttribute("account", account);
        model.addAttribute("assignedClubIds", accounts.assignedClubIds(account));
        model.addAttribute("clubs", clubs.getAllClubsForAdmin());
        return "admin/club-accounts/details";
    }

    @PostMapping("/{id}/clubs")
    public String assign(@PathVariable Long id, @RequestParam(required = false) Set<Long> clubIds,
                         @AuthenticationPrincipal Owner actor) {
        accounts.assign(actor, id, clubIds == null ? Set.of() : clubIds);
        return "redirect:/admin/club-accounts/" + id + "?saved";
    }
}
