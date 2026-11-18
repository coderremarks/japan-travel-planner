package dev.tabiji.admin.api;

import dev.tabiji.admin.application.AdminStatsService;
import dev.tabiji.admin.application.AdminStatsService.Stats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator endpoints (ROLE_ADMIN, enforced in the filter chain and again by method security). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {

    private final AdminStatsService stats;

    public AdminController(AdminStatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/stats")
    @Operation(summary = "Aggregate usage counts (ADMIN only)")
    public Stats stats() {
        return stats.stats();
    }
}
