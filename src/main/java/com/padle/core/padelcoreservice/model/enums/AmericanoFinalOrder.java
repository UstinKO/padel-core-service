package com.padle.core.padelcoreservice.model.enums;

public enum AmericanoFinalOrder {
    SCORE_DESC("score", false),
    SCORE_ASC("score", true),
    WINS_DESC("wins", false),
    WINS_ASC("wins", true);

    private final String sortBy;
    private final boolean ascending;

    AmericanoFinalOrder(String sortBy, boolean ascending) {
        this.sortBy = sortBy;
        this.ascending = ascending;
    }

    public String sortBy() {
        return sortBy;
    }

    public boolean ascending() {
        return ascending;
    }

    public static AmericanoFinalOrder from(String sortBy, boolean ascending) {
        if ("wins".equals(sortBy)) {
            return ascending ? WINS_ASC : WINS_DESC;
        }
        return ascending ? SCORE_ASC : SCORE_DESC;
    }
}
