package com.georgeizraelian.workorder.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.*;
import org.springframework.util.StringUtils;
import java.util.function.Supplier;

/** Spring Security 6.5 SPA pattern: mask request attributes, accept raw cookie headers. */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler masked = new XorCsrfTokenRequestAttributeHandler();
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
        masked.handle(request, response, token);
        token.get(); // Materialize the deferred cookie; login/logout clear it and the SPA refreshes it.
    }
    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
        return (StringUtils.hasText(request.getHeader(token.getHeaderName())) ? plain : masked)
                .resolveCsrfTokenValue(request, token);
    }
}
