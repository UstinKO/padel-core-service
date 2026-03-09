package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PaymentDto;
import com.padle.core.padelcoreservice.model.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    @Mapping(target = "registrationId", source = "registration.id")
    @Mapping(target = "playerName", source = "registration.player.nombre")
    @Mapping(target = "tournamentName", source = "registration.tournament.nombre")
    PaymentDto toDto(Payment payment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registration", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    Payment toEntity(PaymentDto paymentDto);
}