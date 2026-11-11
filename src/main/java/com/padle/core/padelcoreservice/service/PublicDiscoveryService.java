package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.PublicCatalogFilter;
import com.padle.core.padelcoreservice.dto.PublicCatalogPage;
import com.padle.core.padelcoreservice.dto.PublicHomeResponse;
import com.padle.core.padelcoreservice.dto.PublicClubResponse;
import com.padle.core.padelcoreservice.exception.PublicDiscoveryException;
import com.padle.core.padelcoreservice.mapper.PublicClubMapper;
import com.padle.core.padelcoreservice.mapper.PublicTournamentMapper;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicDiscoveryService {
    private final TournamentService tournamentService;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final PublicTournamentMapper tournamentMapper;
    private final PublicClubMapper clubMapper;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Timed(name = "public.catalog.time", tags = {"service=public-discovery", "operation=catalog"})
    public PublicCatalogPage catalog(PublicCatalogFilter filter, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE
                || (filter.clubId() != null && filter.clubId() < 1)
                || (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to()))) {
            throw new PublicDiscoveryException("INVALID_CATALOG_FILTER");
        }
        var result = tournamentService.getPublicCatalog(filter, PageRequest.of(page, size,
                Sort.by("fechaInicio", "horaInicio", "id")));
        return new PublicCatalogPage(result.getContent().stream()
                .map(value -> tournamentMapper.toPublicDto(value, baseUrl)).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Timed(name = "public.home.time", tags = {"service=public-discovery", "operation=home"})
    public PublicHomeResponse home() {
        var result = tournamentService.getPublicHomeCatalog(PageRequest.of(0, 6,
                Sort.by("fechaInicio", "horaInicio", "id")));
        return new PublicHomeResponse(clubRepository.countByIsActiveTrue(), playerRepository.countByActivoTrue(),
                result.getTotalElements(), result.getContent().stream()
                .map(value -> tournamentMapper.toPublicDto(value, baseUrl)).toList());
    }

    @Timed(name = "public.clubs.time", tags = {"service=public-discovery", "operation=clubs"})
    public List<PublicClubResponse> clubs() {
        return clubRepository.findByIsActiveTrue(Sort.by("nombre", "id")).stream()
                .map(clubMapper::toPublicDto).toList();
    }

    public PublicClubResponse club(Long id) {
        return clubRepository.findByIdAndIsActiveTrue(id).map(clubMapper::toPublicDto)
                .orElseThrow(() -> new PublicDiscoveryException("CLUB_NOT_FOUND"));
    }
}
