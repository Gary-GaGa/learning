package demo.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, TenantEnforcementFilter tenantEnforcementFilter)
      throws Exception {

    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers("/t/*/reports/**").authenticated()
            .requestMatchers("/t/*/me").authenticated()
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
      .addFilterAfter(tenantEnforcementFilter,
        org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

    return http.build();
  }

  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new CompositeAuthoritiesConverter());
    return converter;
  }

  /**
   * Default Spring converter maps "scope"/"scp" to SCOPE_* authorities.
   * This converter extends it with Keycloak roles:
   * - realm_access.roles -> ROLE_<role>
   * - resource_access.<client>.roles -> ROLE_<role>
   */
  static final class CompositeAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final Converter<Jwt, Collection<GrantedAuthority>> scopeConverter =
        new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
      Collection<GrantedAuthority> authorities = new ArrayList<>();
      Collection<GrantedAuthority> fromScope = scopeConverter.convert(jwt);
      if (fromScope != null) {
        authorities.addAll(fromScope);
      }

      authorities.addAll(extractKeycloakRoles(jwt));
      return authorities;
    }

    private static Collection<GrantedAuthority> extractKeycloakRoles(Jwt jwt) {
      Collection<GrantedAuthority> out = new ArrayList<>();

      addRolesFromPath(out, jwt.getClaims(), "realm_access", "roles");

      Object resourceAccess = jwt.getClaims().get("resource_access");
      if (resourceAccess instanceof Map<?, ?> ra) {
        for (Object clientEntry : ra.values()) {
          if (clientEntry instanceof Map<?, ?> clientMap) {
            addRolesFromPath(out, clientMap, "roles");
          }
        }
      }

      return out;
    }

    private static void addRolesFromPath(Collection<GrantedAuthority> out, Map<?, ?> root, String... path) {
      Object current = root;
      for (String key : path) {
        if (!(current instanceof Map<?, ?> m)) {
          return;
        }
        current = m.get(key);
      }

      if (current instanceof Collection<?> roles) {
        for (Object r : roles) {
          String role = Objects.toString(r, "").trim();
          if (!role.isEmpty()) {
            out.add(new SimpleGrantedAuthority("ROLE_" + role));
          }
        }
      }
    }
  }
}
