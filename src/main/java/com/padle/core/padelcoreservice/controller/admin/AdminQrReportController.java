package com.padle.core.padelcoreservice.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminQrReportController {
    @GetMapping("/admin/marketing/qr")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String report() {
        return "admin/qr-report";
    }
    @GetMapping("/admin/marketing/registration-sources")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String registrations() {
        return "admin/registration-sources";
    }
}
