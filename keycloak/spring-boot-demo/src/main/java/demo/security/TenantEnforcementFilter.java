package demo.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantEnforcementFilter extends OncePerRequestFilter {

  private final String tenantClaimName;

  public TenantEnforcementFilter(@Value("${app.tenant.claim-name:tenant_id}") String tenantClaimName) {
    this.tenantClaimName = tenantClaimName;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = Optional.ofNullable(request.getRequestURI()).orElse("");

    // Only enforce on tenant-scoped endpoints: /t/{tenant}/...
    String tenantFromPath = extractTenantFromPath(path);
    if (tenantFromPath == null) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid authentication");
      return;
    }

    Object claim = jwtAuth.getToken().getClaims().get(tenantClaimName);
    String tenantFromToken = claim == null ? null : String.valueOf(claim);

    if (tenantFromToken == null || tenantFromToken.isBlank()) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing tenant claim: " + tenantClaimName);
      return;
    }

    if (!tenantFromPath.equals(tenantFromToken)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant mismatch");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private static String extractTenantFromPath(String path) {
    // Expected: /t/{tenant}/...
    // Examples: /t/acme/me, /t/acme/reports
    if (!path.startsWith("/t/")) {
      return null;
    }

    String remaining = path.substring(3);
    int slash = remaining.indexOf('/');
    if (slash <= 0) {
      return null;
    }

    return remaining.substring(0, slash);
  }
}
