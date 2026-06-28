package com.padle.core.padelcoreservice.config;

import dev.caceresenzo.disposableemaildomains.DisposableEmailDomains;
import dev.caceresenzo.disposableemaildomains.checker.HttpChecker;
import dev.caceresenzo.disposableemaildomains.spring.boot.autoconfigure.DisposableEmailDomainsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class DisposableEmailConfig {

    private final DisposableEmailDomainsProperties properties;

    @Value("${disposable-email-domains.allowed-domains:}")
    private List<String> allowedDomains;

    /**
     * Переопределяем бин библиотеки (она использует @ConditionalOnMissingBean),
     * добавляя поддержку allowlist для легитимных privacy email-сервисов.
     */
    @Bean
    public DisposableEmailDomains disposableEmailDomains() {
        var builder = DisposableEmailDomains.builder();
        var checkers = properties.getCheckers();

        if (checkers.isDailyUpdatedDomains()) {
            builder.githubDailyDisposableEmailDomains();
        }
        for (var file : checkers.getFile()) {
            builder.file(Path.of(file.getPath()), file.isIgnoreIfMissing());
        }
        for (var http : checkers.getHttp()) {
            var httpBuilder = HttpChecker.builder().uri(http.getUri());
            if (http.getCachePath() != null) {
                var path = Path.of(http.getCachePath());
                if (http.getCacheDuration() != null) {
                    httpBuilder.cache(path, http.getCacheDuration());
                } else {
                    httpBuilder.cache(path);
                }
            }
            builder.checker(httpBuilder.build());
        }
        if (!checkers.getStaticDomains().isEmpty()) {
            builder.staticDomains(checkers.getStaticDomains());
        }

        DisposableEmailDomains delegate = builder.build();
        Set<String> allowlist = allowedDomains.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        return new AllowlistDisposableEmailDomains(delegate, allowlist);
    }

    record AllowlistDisposableEmailDomains(
            DisposableEmailDomains delegate,
            Set<String> allowlist
    ) implements DisposableEmailDomains {

        @Override
        public boolean testDomain(String domain) {
            if (allowlist.contains(domain.toLowerCase())) return false;
            return delegate.testDomain(domain);
        }

        @Override
        public void reload(boolean force) {
            delegate.reload(force);
        }
    }
}
