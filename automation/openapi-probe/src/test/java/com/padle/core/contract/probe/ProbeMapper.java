package com.padle.core.contract.probe;

import com.padle.core.contract.probe.generated.DiscoveryClub;
import com.padle.core.contract.probe.generated.DiscoveryTournament;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProbeMapper {
    ClubView toView(DiscoveryClub club);

    DiscoveryClub toApi(ClubView club);

    TournamentView toView(DiscoveryTournament tournament);
}
