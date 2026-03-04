package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.KingOfCourtMatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KingOfCourtMatchResultRepository extends JpaRepository<KingOfCourtMatchResult, Long> {
}