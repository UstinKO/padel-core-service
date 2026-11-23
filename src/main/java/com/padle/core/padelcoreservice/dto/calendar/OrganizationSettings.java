package com.padle.core.padelcoreservice.dto.calendar;

import java.util.List;

public record OrganizationSettings(Long accountId, List<OrganizationAccount> accounts) {}
