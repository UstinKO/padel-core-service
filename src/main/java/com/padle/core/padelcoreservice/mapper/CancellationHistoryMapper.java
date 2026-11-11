package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.CancellationHistory.Entry;
import com.padle.core.padelcoreservice.repository.CancellationHistoryRepository.Row;
import com.padle.core.padelcoreservice.service.PairTermsService.Cancellation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel="spring")
public interface CancellationHistoryMapper {
    Entry entry(Row source);
    List<Entry> entries(List<Row> source);
    @Mapping(target="token",source="token")
    Cancellation receipt(Cancellation source,String token);
}
