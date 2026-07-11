package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.ClubService;
import com.padle.core.padelcoreservice.service.OwnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/clubs")
@RequiredArgsConstructor
@Slf4j
public class AdminClubController {

    private final ClubService clubService;
    private final OwnerService ownerService;

    @GetMapping
    public String listClubs(Model model, @AuthenticationPrincipal Owner owner) {
        log.info("Listing all clubs for admin");
        model.addAttribute("clubs", clubService.getAllClubsForAdmin());
        model.addAttribute("isSuperAdmin", owner.isSuperAdmin());
        model.addAttribute("isAdminRole", owner.isAdminRole());
        return "admin/clubs/list";
    }

    @GetMapping("/new")
    public String newClubForm(Model model, @AuthenticationPrincipal Owner owner,
                              org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (owner.isAdminRole()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para crear clubes");
            return "redirect:/admin/clubs";
        }
        log.info("Showing new club form");
        model.addAttribute("club", new ClubDto());
        return "admin/clubs/form";
    }

    // Просмотр — доступен всем
    @GetMapping("/{id}")
    public String viewClub(@PathVariable Long id, Model model,
                           @AuthenticationPrincipal Owner owner,
                           RedirectAttributes redirectAttributes) {
        log.info("Viewing club details: {}", id);
        try {
            model.addAttribute("club", clubService.getClubByIdForAdmin(id));
            model.addAttribute("isSuperAdmin", owner.isSuperAdmin());
            return "admin/clubs/details";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Club no encontrado");
            return "redirect:/admin/clubs";
        }
    }

    // Форма редактирования — только SUPER_ADMIN
    @GetMapping("/{id}/edit")
    public String editClubForm(@PathVariable Long id, Model model,
                               @AuthenticationPrincipal Owner owner,
                               RedirectAttributes redirectAttributes) {
        if (!owner.isSuperAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No tienes permiso para editar clubes");
            return "redirect:/admin/clubs";
        }
        log.info("Showing edit form for club: {}", id);
        try {
            model.addAttribute("club", clubService.getClubByIdForAdmin(id));
            return "admin/clubs/form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Club no encontrado");
            return "redirect:/admin/clubs";
        }
    }

    @PostMapping
    public String createClub(@Valid @ModelAttribute("club") ClubDto clubDto,
                             BindingResult result,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        if (userDetails instanceof Owner owner && owner.isAdminRole()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para crear clubes");
            return "redirect:/admin/clubs";
        }
        log.info("Creating new club");

        if (result.hasErrors()) {
            return "admin/clubs/form";
        }

        try {
            Owner owner = ownerService.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Owner not found"));

            ClubDto created = clubService.createClub(clubDto, owner.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Club '" + created.getNombre() + "' creado exitosamente");
            return "redirect:/admin/clubs/" + created.getId();
        } catch (Exception e) {
            log.error("Error creating club", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al crear club: " + e.getMessage());
            return "redirect:/admin/clubs/new";
        }
    }

    // Сохранение правок — только SUPER_ADMIN
    @PostMapping("/{id}/edit")
    public String updateClub(@PathVariable Long id,
                             @Valid @ModelAttribute("club") ClubDto clubDto,
                             BindingResult result,
                             @AuthenticationPrincipal Owner owner,
                             RedirectAttributes redirectAttributes) {
        if (!owner.isSuperAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No tienes permiso para editar clubes");
            return "redirect:/admin/clubs";
        }
        log.info("Updating club: {}", id);

        if (result.hasErrors()) {
            return "admin/clubs/form";
        }

        try {
            ClubDto updated = clubService.updateClub(id, clubDto, owner);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Club '" + updated.getNombre() + "' actualizado exitosamente");
            return "redirect:/admin/clubs/" + updated.getId();
        } catch (Exception e) {
            log.error("Error updating club", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al actualizar club: " + e.getMessage());
            return "redirect:/admin/clubs/" + id + "/edit";
        }
    }

    // Переключение статуса — только SUPER_ADMIN
    @PostMapping("/{id}/toggle-status")
    public String toggleClubStatus(@PathVariable Long id,
                                   @AuthenticationPrincipal Owner owner,
                                   RedirectAttributes redirectAttributes) {
        if (!owner.isSuperAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No tienes permiso para cambiar el estado de los clubes");
            return "redirect:/admin/clubs";
        }
        log.info("Toggling club status: {}", id);
        try {
            clubService.toggleClubStatus(id);
            redirectAttributes.addFlashAttribute("successMessage", "Estado del club actualizado");
        } catch (Exception e) {
            log.error("Error toggling club status", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cambiar estado: " + e.getMessage());
        }
        return "redirect:/admin/clubs";
    }

    // Удаление — только SUPER_ADMIN
    @PostMapping("/{id}/delete")
    public String deleteClub(@PathVariable Long id,
                             @AuthenticationPrincipal Owner owner,
                             RedirectAttributes redirectAttributes) {
        if (!owner.isSuperAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No tienes permiso para eliminar clubes");
            return "redirect:/admin/clubs";
        }
        log.info("Soft deleting club: {}", id);
        try {
            clubService.deleteClub(id, owner);
            redirectAttributes.addFlashAttribute("successMessage", "Club desactivado exitosamente");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Club no encontrado");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para eliminar clubes");
        } catch (Exception e) {
            log.error("Error deleting club", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al desactivar club: " + e.getMessage());
        }
        return "redirect:/admin/clubs";
    }
}