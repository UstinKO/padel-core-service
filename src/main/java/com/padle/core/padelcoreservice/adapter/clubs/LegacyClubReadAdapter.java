package com.padle.core.padelcoreservice.adapter.clubs;

import com.padle.core.application.clubs.ClubDetails;
import com.padle.core.application.clubs.ClubReadPort;
import com.padle.core.application.clubs.InternalClubReadPort;
import com.padle.core.application.clubs.PublicClub;
import com.padle.core.padelcoreservice.mapper.ClubReadModelMapper;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LegacyClubReadAdapter implements ClubReadPort, InternalClubReadPort {
    private final ClubRepository clubs;
    private final ClubReadModelMapper mapper;

    @Override
    public List<PublicClub> findActiveOrderedByNameAndId() {
        return clubs.findByIsActiveTrue(Sort.by("nombre", "id")).stream()
                .map(mapper::toReadModel).toList();
    }

    @Override
    public Optional<PublicClub> findActiveById(Long id) {
        return clubs.findByIdAndIsActiveTrue(id).map(mapper::toReadModel);
    }

    @Override
    public long countActive() {
        return clubs.countByIsActiveTrue();
    }

    @Override
    public Optional<ClubDetails> findById(Long id) {
        return clubs.findById(id).map(mapper::toDetails);
    }

    @Override
    public Map<Long, ClubDetails> findByIds(Collection<Long> ids) {
        return clubs.findAllById(ids).stream()
                .collect(Collectors.toMap(Club::getId, mapper::toDetails));
    }
}
