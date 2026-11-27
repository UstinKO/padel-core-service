package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.service.PairTermsService.Cancellation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel="spring")
public interface CancellationHistoryMapper {
    @Mapping(target="token",source="token")
    Cancellation receipt(Cancellation source,String token);
}
