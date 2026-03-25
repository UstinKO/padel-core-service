package com.padle.core.padelcoreservice.dto.americano;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingCriteria {
    private String sortBy; // "score" или "wins"
    private boolean ascending;

    public String getSortBy() {
        return sortBy != null ? sortBy : "score";
    }
}