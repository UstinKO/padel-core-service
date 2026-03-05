package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import com.padle.core.padelcoreservice.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerPadelController {

    private final PlayerService playerService;

    @GetMapping("/registro")
    public String mostrarFormularioRegistro(Model model) {
        if (!model.containsAttribute("registroRequest")) {
            model.addAttribute("registroRequest", new RegistroRequestDto());
        }
        return "registro";
    }

    @PostMapping("/registro")
    public String registrarJugador(
            @Valid @ModelAttribute("registroRequest") RegistroRequestDto request,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        log.info("Recibida solicitud de registro para: {}", request.getEmail());

        // Limpiar espacios
        if (request.getNombre() != null) request.setNombre(request.getNombre().trim());
        if (request.getApellido() != null) request.setApellido(request.getApellido().trim());
        if (request.getEmail() != null) request.setEmail(request.getEmail().trim());
        if (request.getTelefono() != null && request.getTelefono().trim().isEmpty()) {
            request.setTelefono(null);
        }

        // Verificar que las contraseñas coinciden
        if (!request.passwordsMatch()) {
            log.warn("Las contraseñas no coinciden");
            redirectAttributes.addFlashAttribute("errorMessage", "Las contraseñas no coinciden");
            return "redirect:/players/registro";
        }

        // Si hay errores de validación
        if (result.hasErrors()) {
            log.warn("Errores de validación en el formulario:");
            result.getAllErrors().forEach(error ->
                    log.warn("Error: {}", error.getDefaultMessage())
            );

            String errorMessage = result.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .findFirst()
                    .orElse("Por favor, corrige los errores en el formulario");

            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            return "redirect:/players/registro";
        }

        try {
            // Intentar registrar
            PlayerResponseDto jugadorRegistrado = playerService.registrarJugador(request);
            log.info("Jugador registrado exitosamente: {}", jugadorRegistrado.getEmail());

            // REDIRECT A LOGIN CON PARÁMETRO DE ÉXITO
            return "redirect:/login?registered=true";

        } catch (IllegalArgumentException e) {
            log.error("Error al registrar jugador: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/players/registro";
        }
    }

    // ========== RESTO DEL CONTROLADOR ==========

    // API REST
    @PostMapping("/api/registro")
    @ResponseBody
    public ResponseEntity<?> registrarJugadorApi(@Valid @RequestBody RegistroRequestDto request) {
        try {
            PlayerResponseDto jugadorRegistrado = playerService.registrarJugador(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(jugadorRegistrado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<PlayerResponseDto>> obtenerTodosJugadoresApi() {
        List<PlayerResponseDto> jugadores = playerService.obtenerTodosJugadores();
        return ResponseEntity.ok(jugadores);
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerJugadorPorIdApi(@PathVariable Long id) {
        try {
            PlayerResponseDto jugador = playerService.obtenerJugadorPorId(id);
            return ResponseEntity.ok(jugador);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/api/confirmar-email")
    @ResponseBody
    public ResponseEntity<?> confirmarEmailApi(@RequestParam String codigo) {
        try {
            PlayerResponseDto jugador = playerService.confirmarEmail(codigo);
            return ResponseEntity.ok("Email confirmado exitosamente para: " + jugador.getEmail());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}