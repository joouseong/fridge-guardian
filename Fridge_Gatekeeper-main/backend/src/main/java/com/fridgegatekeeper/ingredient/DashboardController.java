package com.fridgegatekeeper.ingredient;

import com.fridgegatekeeper.user.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping("/api/dashboard")
    public DashboardResponse get(Authentication authentication) {
        return dashboard.dashboard(CurrentUser.id(authentication));
    }
}
