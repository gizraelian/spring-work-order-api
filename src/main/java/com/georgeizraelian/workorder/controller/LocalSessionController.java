package com.georgeizraelian.workorder.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@Profile("local-browser")
@RequestMapping("/api/local-auth")
public class LocalSessionController {
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        // The SPA handler loads the deferred token into its readable cookie, never a JSON body.
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/session")
    public ResponseEntity<Map<String, Boolean>> session(Authentication authentication) {
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("authenticated", authenticated));
    }
}
