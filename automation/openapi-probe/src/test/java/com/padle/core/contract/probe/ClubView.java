package com.padle.core.contract.probe;

import java.net.URI;

public record ClubView(Long id, String name, String address, String city, String description,
                       String logoUrl, String mapUrl, String websiteUrl, URI canonicalUrl) {
}
