package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.config.RecaptchaProperties;
import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.RecaptchaService;
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
    private final RecaptchaService recaptchaService;
    private final RecaptchaProperties recaptchaProperties;

    @GetMapping("/registro")
    public String mostrarFormularioRegistro(Model model) {
        if (!model.containsAttribute("registroRequest")) {
            model.addAttribute("registroRequest", new RegistroRequestDto());
        }
        model.addAttribute("recaptchaSiteKey", recaptchaProperties.getSiteKey());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrarJugador(
            @Valid @ModelAttribute("registroRequest") RegistroRequestDto request,
            BindingResult result,
            @RequestParam(value = "g-recaptcha-response", required = false) String recaptchaToken,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Recibida solicitud de registro para: {}", request.getEmail());

        // Проверяем reCAPTCHA
        if (!recaptchaService.verify(recaptchaToken, "register")) {
            model.addAttribute("recaptchaSiteKey", recaptchaProperties.getSiteKey());
            model.addAttribute("registroRequest", request); // ← сохраняем введённые данные
            model.addAttribute("errorMessage",
                    "Verificación de seguridad fallida. Por favor intenta de nuevo.");
            return "registro";
        }

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
            log.info("Jugador registrado exitosamente: {}", jugadorRegistrado.getEmail());
            String encodedEmail = java.net.URLEncoder.encode(
                    jugadorRegistrado.getEmail(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
            return "redirect:/?registroExitoso=true&email=" + encodedEmail;

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