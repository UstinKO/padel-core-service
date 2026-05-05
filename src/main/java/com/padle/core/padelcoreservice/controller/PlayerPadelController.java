package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.config.RecaptchaProperties;
import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.security.RateLimitFilter;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.RecaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    private final RateLimitFilter rateLimitFilter;

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
            @RequestParam(value = "website", required = false) String honeypot,
            RedirectAttributes redirectAttributes,
            HttpServletRequest httpRequest) {

        log.info("Recibida solicitud de registro para: {}", request.getEmail());

        // HONEYPOT ПРОВЕРКА ПЕРВЫМ ДЕЛОМ
        if (honeypot != null && !honeypot.isBlank()) {
            log.warn("🪤 Honeypot triggered! IP: {}, honeypot value: '{}'",
                    httpRequest.getRemoteAddr(), honeypot);
            // Бот думает что всё ок, но мы его помечаем
            rateLimitFilter.markRegistrationFailed(httpRequest.getRemoteAddr());
            return "redirect:/?welcome=true";
        }

        // Проверяем reCAPTCHA
        if (!recaptchaService.verify(recaptchaToken, "register")) {
            log.warn("reCAPTCHA fallida para IP: {}", httpRequest.getRemoteAddr());
            rateLimitFilter.markRegistrationFailed(httpRequest.getRemoteAddr());  // ← фейл
            model.addAttribute("recaptchaSiteKey", recaptchaProperties.getSiteKey());
            model.addAttribute("registroRequest", request);
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
            log.warn("Las contraseñas no coinciden para IP: {}", httpRequest.getRemoteAddr());
            rateLimitFilter.markRegistrationFailed(httpRequest.getRemoteAddr());  // ← фейл

            // Искусственная задержка при ошибке
            sleepRandom(2, 5);

            redirectAttributes.addFlashAttribute("errorMessage", "Las contraseñas no coinciden");
            return "redirect:/players/registro";
        }

        // Si hay errores de validación
        if (result.hasErrors()) {
            log.warn("Errores de validación en el formulario. IP: {}", httpRequest.getRemoteAddr());
            result.getAllErrors().forEach(error ->
                    log.warn("Error: {}", error.getDefaultMessage())
            );

            rateLimitFilter.markRegistrationFailed(httpRequest.getRemoteAddr());  // ← фейл

            String errorMessage = result.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .findFirst()
                    .orElse("Por favor, corrige los errores en el formulario");

            // Искусственная задержка при ошибке
            sleepRandom(2, 5);

            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            return "redirect:/players/registro";
        }

        try {
            // 1. Регистрируем игрока
            PlayerResponseDto jugadorRegistrado = playerService.registrarJugador(request);
            log.info("✅ Jugador registrado: {} (IP: {})", jugadorRegistrado.getEmail(), httpRequest.getRemoteAddr());

            // 2. Автоматический вход
            try {
                PlayerPadel player = playerService.getPlayerByEmail(request.getEmail());

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                player,
                                null,
                                player.getAuthorities()
                        );

                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(authToken);
                SecurityContextHolder.setContext(securityContext);

                HttpSession session = httpRequest.getSession(true);
                session.setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        securityContext
                );

                log.info("✅ Usuario autenticado automáticamente: {}", request.getEmail());

            } catch (Exception e) {
                log.error("❌ No se pudo autenticar automáticamente: {}", e.getMessage());
            }

            // 3. Редирект на главную
            return "redirect:/?welcome=true";

        } catch (IllegalArgumentException e) {
            log.error("Error al registrar jugador: {} (IP: {})", e.getMessage(), httpRequest.getRemoteAddr());

            // ← ФЕЙЛ — увеличиваем счётчик
            rateLimitFilter.markRegistrationFailed(httpRequest.getRemoteAddr());

            // Искусственная задержка 2-5 секунд
            sleepRandom(2, 5);

            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/players/registro";
        }
    }

    // Вспомогательный метод в том же контроллере
    private void sleepRandom(int minSeconds, int maxSeconds) {
        try {
            long delay = minSeconds * 1000L + (long) (Math.random() * (maxSeconds - minSeconds) * 1000L);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== RESTO DEL CONTROLADOR (без изменений) ==========

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