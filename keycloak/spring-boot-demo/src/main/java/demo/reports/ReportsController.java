package demo.reports;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/t/{tenant}")
public class ReportsController {

  private final InMemoryReportRepository repo;

  public ReportsController(InMemoryReportRepository repo) {
    this.repo = repo;
  }

  @GetMapping("/me")
  public Map<String, Object> me(@PathVariable String tenant, JwtAuthenticationToken auth) {
    return Map.of(
        "tenantFromPath", tenant,
        "subject", auth.getToken().getSubject(),
        "tenant_id", auth.getToken().getClaims().get("tenant_id"),
        "scope", auth.getToken().getClaims().get("scope"),
        "authorities", auth.getAuthorities().stream().map(a -> a.getAuthority()).toList());
  }

  @PreAuthorize("hasAuthority('SCOPE_reports:read')")
  @GetMapping("/reports")
  public List<Report> list(@PathVariable String tenant) {
    return repo.listByTenant(tenant);
  }

  public record CreateReportRequest(String title) {}

  @PreAuthorize("hasAuthority('SCOPE_reports:write')")
  @PostMapping("/reports")
  @ResponseStatus(HttpStatus.CREATED)
  public Report create(@PathVariable String tenant, @RequestBody CreateReportRequest req, JwtAuthenticationToken auth) {
    String title = req == null || req.title() == null ? "" : req.title().trim();
    if (title.isEmpty()) {
      title = "untitled";
    }
    return repo.create(tenant, auth.getToken().getSubject(), title);
  }

  @PreAuthorize("hasAuthority('SCOPE_reports:read')")
  @GetMapping("/reports/{id}")
  public Report get(@PathVariable String tenant, @PathVariable String id, JwtAuthenticationToken auth) {
    Report report = repo.findById(id).orElseThrow(() -> new ReportNotFoundException(id));

    // Resource-level enforcement (data layer check): ensure record belongs to tenant.
    if (!report.tenantId().equals(tenant)) {
      throw new ReportForbiddenException();
    }

    // Optional: owner check example
    // if (!report.ownerSub().equals(auth.getToken().getSubject())) { ... }

    return report;
  }
}
