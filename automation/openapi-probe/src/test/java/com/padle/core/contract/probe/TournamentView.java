package com.padle.core.contract.probe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TournamentView(BigDecimal price, OffsetDateTime startsAt, String timeZone) {
}
