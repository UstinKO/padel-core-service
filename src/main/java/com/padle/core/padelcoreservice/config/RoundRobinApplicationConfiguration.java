package com.padle.core.padelcoreservice.config;

import com.padle.core.application.roundrobin.RoundRobinFinishCommand;
import com.padle.core.application.roundrobin.RoundRobinPositionCommand;
import com.padle.core.application.roundrobin.RoundRobinRankingQuery;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({RoundRobinRankingQuery.class, RoundRobinPositionCommand.class, RoundRobinFinishCommand.class})
public class RoundRobinApplicationConfiguration {
}
