package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.*;
import com.padle.core.padelcoreservice.receipts.ReceiptReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tournaments/{tournament}/receipts")
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ORGANIZER','ADMIN','CLUB_ADMIN')")
@RequiredArgsConstructor
public class PaymentReceiptController {
    private final ReceiptReviewService receipts;

    @GetMapping
    public ResponseEntity<List<Receipt>> list(@AuthenticationPrincipal Owner owner, @PathVariable long tournament) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipts.list(owner, tournament));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal Owner owner, @PathVariable long tournament, @PathVariable UUID id) {
        var file = receipts.file(owner, tournament, id);
        String extension = switch (file.contentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".pdf";
        };
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(file.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("receipt-" + id + extension).build().toString())
                .body(file.content());
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Receipt> accept(@AuthenticationPrincipal Owner owner, @PathVariable long tournament,
                                          @PathVariable UUID id, @Valid @RequestBody Accept decision) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipts.accept(owner, tournament, id, decision));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Receipt> reject(@AuthenticationPrincipal Owner owner, @PathVariable long tournament,
                                          @PathVariable UUID id, @Valid @RequestBody Reject decision) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipts.reject(owner, tournament, id, decision));
    }
}
