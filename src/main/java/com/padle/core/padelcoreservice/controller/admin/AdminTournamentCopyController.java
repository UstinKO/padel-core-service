package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.service.ClubService;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/tournaments")
@RequiredArgsConstructor
@Slf4j
public class AdminTournamentCopyController {

    private final TournamentService tournamentService;
    private final ClubService clubService;

    @GetMapping("/{id}/copy")
    public String copyTournament(@PathVariable Long id, Model model) {
        log.info("Copying tournament with id: {}", id);

        // Получаем существующий турнир
        TournamentDto sourceTournament = tournamentService.getTournamentDtoById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found with id: " + id));

        // Создаем новый DTO с данными из исходного турнира
        TournamentDto newTournament = new TournamentDto();

        // Копируем все поля, кроме ID и дат создания/обновления
        newTournament.setClubId(sourceTournament.getClubId());
        newTournament.setNombre(sourceTournament.getNombre() + " (copia)");
        newTournament.setFechaInicio(sourceTournament.getFechaInicio());
        newTournament.setHoraInicio(sourceTournament.getHoraInicio());
        newTournament.setDuracion(sourceTournament.getDuracion());
        newTournament.setGeneroFormato(sourceTournament.getGeneroFormato());
        newTournament.setCategoriaNivel(sourceTournament.getCategoriaNivel());
        newTournament.setTipo(sourceTournament.getTipo());
        newTournament.setModalidad(sourceTournament.getModalidad());
        newTournament.setCupoMax(sourceTournament.getCupoMax());
        newTournament.setPrecio(sourceTournament.getPrecio());
        newTournament.setMoneda(sourceTournament.getMoneda());
        newTournament.setDeadlineCancelacion(sourceTournament.getDeadlineCancelacion());
        newTournament.setInfoDetallada(sourceTournament.getInfoDetallada());
        newTournament.setContactoOrganizador(sourceTournament.getContactoOrganizador());
        newTournament.setFaqUrl(sourceTournament.getFaqUrl());

        // Устанавливаем статус по умолчанию для нового турнира
        newTournament.setEstado(TournamentStatus.REGISTRO_ABIERTO);

        // Добавляем данные в модель
        model.addAttribute("tournament", newTournament);
        model.addAttribute("clubs", clubService.getAllClubs());
        model.addAttribute("genderFormats", GenderFormat.values());
        model.addAttribute("niveles", Nivel.values());
        model.addAttribute("tournamentTypes", TournamentType.values());
        model.addAttribute("modalidades", Modalidad.values());
        model.addAttribute("tournamentStatuses", TournamentStatus.values());

        // Добавляем флаг, что это копирование (для отображения в заголовке)
        model.addAttribute("isCopy", true);

        // ИСПРАВЛЕНО: правильный путь к шаблону
        return "admin/tournaments/form";
    }
}