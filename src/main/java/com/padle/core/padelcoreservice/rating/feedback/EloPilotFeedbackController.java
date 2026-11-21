package com.padle.core.padelcoreservice.rating.feedback;

import com.padle.core.padelcoreservice.rating.EloPilotAccess;
import com.padle.core.padelcoreservice.rating.EloPilotAudience;
import com.padle.core.padelcoreservice.rating.feedback.EloPilotFeedbackRepository.Source;
import com.padle.core.padelcoreservice.rating.feedback.EloPilotFeedbackRepository.Topic;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.UUID;

@Controller
@ConditionalOnProperty(name = "app.rating.pilot.feedback-enabled", havingValue = "true")
@RequiredArgsConstructor
public class EloPilotFeedbackController {
    public record Submission(@NotNull UUID requestKey, @NotNull Source source, @NotNull Topic topic,
                             @NotBlank @Size(max = 2000) String message) { }

    private final ObjectProvider<EloPilotAudience> audience;
    private final EloPilotAccess access;
    private final EloPilotFeedbackService feedback;
    private final EloPilotFeedbackRepository repository;

    @PostMapping("/api/rating/feedback")
    @ResponseBody
    public ResponseEntity<Void> submit(@Valid @ModelAttribute Submission submission, BindingResult validation,
                                       Authentication authentication, HttpServletResponse response) {
        if (audience.stream().noneMatch(pilot -> pilot.allows(authentication))) {
            throw new AccessDeniedException("Нет доступа к пилоту Elo");
        }
        access.protectResponse(response);
        if (validation.hasErrors()) {
            return ResponseEntity.badRequest().build();
        }
        feedback.submit(SecurityUtils.extractPlayer(authentication.getPrincipal()), submission.requestKey(),
                submission.source(), submission.topic(), submission.message().strip());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/rating-feedback")
    public String inbox(@RequestParam(required = false) Long before, Model model, HttpServletResponse response) {
        access.protectResponse(response);
        var entries = repository.latest(before);
        model.addAttribute("entries", entries);
        model.addAttribute("nextBefore", entries.size() == 50 ? entries.get(entries.size() - 1).id() : null);
        return "admin/rating-feedback";
    }
}
