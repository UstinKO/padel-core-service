package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.PublicCatalogFilter;
import com.padle.core.padelcoreservice.dto.PublicCatalogPage;
import com.padle.core.padelcoreservice.dto.PublicHomeResponse;
import com.padle.core.padelcoreservice.dto.PublicClubResponse;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.PublicDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicDiscoveryController {
    private final PublicDiscoveryService service;

    @GetMapping("/catalog")
    public ResponseEntity<PublicCatalogPage> catalog(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Long clubId, @RequestParam(required = false) TournamentType type,
            @RequestParam(required = false) Modalidad modality, @RequestParam(required = false) GenderFormat gender,
            @RequestParam(required = false) Nivel level, @RequestParam(required = false) TournamentStatus status,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return response(service.catalog(new PublicCatalogFilter(clubId, type, modality, gender, level, status, from, to), page, size));
    }

    @GetMapping("/home")
    public ResponseEntity<PublicHomeResponse> home() { return response(service.home()); }

    @GetMapping("/clubs")
    public ResponseEntity<List<PublicClubResponse>> clubs() { return response(service.clubs()); }

    @GetMapping("/clubs/{id}")
    public ResponseEntity<PublicClubResponse> club(@PathVariable Long id) { return response(service.club(id)); }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
