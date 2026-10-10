package com.georgeizraelian.workorder.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@Profile("local-browser")
public class LocalBrowserSecurityConfig {
    @Bean
    SecurityFilterChain localBrowserChain(HttpSecurity http, Environment environment) throws Exception {
        boolean httpException = environment.getProperty("app.local-browser.allow-loopback-http", Boolean.class, false);
        String origin = httpException ? "http://127.0.0.1:4200" : "https://127.0.0.1:4200";
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName("M4C-XSRF");
        csrf.setHeaderName("X-M4C-XSRF");
        csrf.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(!httpException));
        http.httpBasic(basic -> basic.disable())
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(cache -> cache.disable())
                .cors(cors -> cors.disable())
                .addFilterBefore(new LocalBoundaryFilter(origin), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/local-auth/csrf", "/api/local-auth/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/work-orders", "/api/work-orders/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/local-auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> res.setStatus(401))
                        .accessDeniedHandler((req, res, ex) -> res.setStatus(403)))
                .formLogin(login -> login.loginProcessingUrl("/api/local-auth/login")
                        .successHandler((req, res, auth) -> res.setStatus(204))
                        .failureHandler((req, res, ex) -> res.setStatus(401)))
                .logout(logout -> logout.logoutUrl("/api/local-auth/logout")
                        .invalidateHttpSession(true).clearAuthentication(true)
                        .addLogoutHandler((req, res, auth) -> res.addHeader("Set-Cookie",
                                org.springframework.http.ResponseCookie.from("M4CSESSION", "")
                                        .path("/api").httpOnly(true).sameSite("Strict").secure(!httpException)
                                        .maxAge(0).build().toString()))
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
        return http.build();
    }

    /** Origin + literal loopback Host checks augment CSRF; no CORS/preflight allowance. */
    static final class LocalBoundaryFilter extends OncePerRequestFilter {
        private final String origin;
        private static final Set<String> SAFE = Set.of("GET", "HEAD");
        LocalBoundaryFilter(String origin) { this.origin = origin; }
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String host = req.getServerName();
            String requestOrigin = req.getHeader("Origin");
            if (!"127.0.0.1".equals(host) || (requestOrigin != null && !origin.equals(requestOrigin))
                    || (!SAFE.contains(req.getMethod()) && !origin.equals(requestOrigin))) {
                res.setStatus(403); return;
            }
            if (req.getHeader("Authorization") != null) { res.setStatus(401); return; }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!SAFE.contains(req.getMethod()) && req.getRequestURI().startsWith("/api/work-orders")
                    && (auth == null || !auth.isAuthenticated())) {
                res.setStatus(401); return;
            }
            chain.doFilter(req, res);
        }
    }
}
