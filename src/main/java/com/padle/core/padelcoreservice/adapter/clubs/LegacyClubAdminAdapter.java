package com.padle.core.padelcoreservice.adapter.clubs;

import com.padle.core.application.clubs.ClubAdminReadPort;
import com.padle.core.application.clubs.ClubAdminWritePort;
import com.padle.core.application.clubs.ClubProfile;
import com.padle.core.padelcoreservice.mapper.ClubMapper;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LegacyClubAdminAdapter implements ClubAdminReadPort, ClubAdminWritePort {
    private final ClubRepository clubs;
    private final ClubMapper mapper;

    @Override
    public List<ClubProfile> findAll() {
        return profiles(clubs.findAll());
    }

    @Override
    public List<ClubProfile> findActive() {
        return profiles(clubs.findByIsActiveTrue());
    }

    @Override
    public Optional<ClubProfile> findProfileById(Long id) {
        return clubs.findById(id).map(mapper::toProfile);
    }

    @Override
    public Optional<ClubProfile> findByName(String name) {
        return clubs.findByNombre(name).map(mapper::toProfile);
    }

    @Override
    public List<ClubProfile> findByCity(String city) {
        return profiles(clubs.findByZonaCiudad(city));
    }

    @Override
    public List<ClubProfile> search(String term) {
        return profiles(clubs.searchClubs(term));
    }

    @Override
    public List<ClubProfile> findAllOrderedByName() {
        return profiles(clubs.findAllByOrderByNombreAsc());
    }

    @Override
    public List<ClubProfile> findActiveOrderedByName() {
        return profiles(clubs.findAllByIsActiveTrueOrderByNombreAsc());
    }

    @Override
    public boolean existsByName(String name) {
        return clubs.existsByNombre(name);
    }

    @Override
    public boolean existsActiveById(Long id) {
        return clubs.existsByIdAndIsActiveTrue(id);
    }

    @Override
    public Set<Long> findExistingIds(Set<Long> ids) {
        return clubs.findAllById(ids).stream().map(Club::getId).collect(Collectors.toSet());
    }

    @Override
    public ClubProfile create(ClubProfile.Fields fields, Long createdBy) {
        Club club = mapper.toEntity(fields);
        club.setCreatedBy(createdBy);
        club.setIsActive(true);
        return mapper.toProfile(clubs.save(club));
    }

    @Override
    public ClubProfile update(Long id, ClubProfile.Fields fields) {
        Club club = clubs.getReferenceById(id);
        mapper.update(fields, club);
        return mapper.toProfile(clubs.save(club));
    }

    @Override
    public void setActive(Long id, boolean active) {
        Club club = clubs.getReferenceById(id);
        club.setIsActive(active);
        clubs.save(club);
    }

    @Override
    public void delete(Long id) {
        clubs.deleteById(id);
    }

    private List<ClubProfile> profiles(List<Club> entities) {
        return entities.stream().map(mapper::toProfile).toList();
    }
}
