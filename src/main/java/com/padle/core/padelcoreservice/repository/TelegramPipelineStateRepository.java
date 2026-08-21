package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.TelegramPipelineState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramPipelineStateRepository extends JpaRepository<TelegramPipelineState, Long> {
}
