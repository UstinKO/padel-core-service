package com.padle.core.padelcoreservice.dto.live;

import com.padle.core.padelcoreservice.dto.PublicEventResponse.Format;

public sealed interface PublicLiveSnapshot permits PublicLiveDtos.King, PublicLiveDtos.Americano, PublicLiveDtos.Playoff, PublicLiveDtos.Pending {
    PublicLiveDtos.Metadata metadata();
    Format format();
}
