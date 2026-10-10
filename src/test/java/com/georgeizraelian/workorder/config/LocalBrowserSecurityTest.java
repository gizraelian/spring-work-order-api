package com.georgeizraelian.workorder.config;

import com.georgeizraelian.workorder.controller.LocalSessionController;
import com.georgeizraelian.workorder.controller.WorkOrderController;
import com.georgeizraelian.workorder.dto.*;
import com.georgeizraelian.workorder.exception.GlobalExceptionHandler;
import com.georgeizraelian.workorder.model.*;
import com.georgeizraelian.workorder.service.WorkOrderService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({LocalSessionController.class, WorkOrderController.class})
@Import({SecurityConfig.class, LocalBrowserSecurityConfig.class, EnvironmentSafetyConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles({"test", "local-browser"})
@TestPropertySource(properties = {"app.local-browser.enabled=true", "app.local-browser.allow-loopback-http=true",
        "server.servlet.session.cookie.secure=false", "APP_USERNAME=browser-fixture", "APP_PASSWORD=fixture-only-password",
        "app.security.username=browser-fixture", "app.security.password=fixture-only-password"})
class LocalBrowserSecurityTest {
    private static final String API = "http://127.0.0.1";
    private static final String ORIGIN = "http://127.0.0.1:4200";
    @Autowired MockMvc mvc;
    @MockitoBean WorkOrderService service;

    private Cookie token() throws Exception {
        Cookie cookie = mvc.perform(get(API + "/api/local-auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("M4C-XSRF");
        assertThat(cookie).isNotNull(); assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getSecure()).isFalse(); assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        return cookie;
    }
    private MockHttpSession login() throws Exception {
        Cookie cookie = token();
        return (MockHttpSession) mvc.perform(post(API + "/api/local-auth/login").header("Origin", ORIGIN)
                        .cookie(cookie).header("X-M4C-XSRF", cookie.getValue())
                        .param("username", "browser-fixture").param("password", "fixture-only-password"))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    private WorkOrderResponse order() {
        return new WorkOrderResponse(1L, "Fictional", null, WorkOrderStatus.OPEN, WorkOrderPriority.HIGH,
                "demo@example.com", null, LocalDateTime.now(), LocalDateTime.now());
    }
    @Test void loginRotatesExistingSessionAndCsrf() throws Exception {
        Cookie cookie = token(); MockHttpSession original = new MockHttpSession(); original.setAttribute("fixture", "preserved");
        String oldId = original.getId();
        MvcResult result = mvc.perform(post(API + "/api/local-auth/login").session(original).header("Origin", ORIGIN)
                .cookie(cookie).header("X-M4C-XSRF", cookie.getValue()).param("username", "browser-fixture")
                .param("password", "fixture-only-password")).andExpect(status().isNoContent()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull(); assertThat(session.getId()).isNotEqualTo(oldId);
        assertThat(session.getAttribute("fixture")).isEqualTo("preserved");
        assertThat(result.getResponse().getCookie("M4C-XSRF").getMaxAge()).isZero();
        assertThat(token().getValue()).isNotEqualTo(cookie.getValue());
        mvc.perform(get(API + "/api/local-auth/session").session(session)).andExpect(jsonPath("$.authenticated").value(true));
    }
    @Test void incorrectLoginReturns401WithoutBasicChallenge() throws Exception {
        Cookie cookie = token();
        mvc.perform(post(API + "/api/local-auth/login").header("Origin", ORIGIN).cookie(cookie)
                .header("X-M4C-XSRF", cookie.getValue()).param("username", "browser-fixture").param("password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }
    @Test void loginMissingOrInvalidCsrfIsRejected() throws Exception {
        mvc.perform(post(API + "/api/local-auth/login").header("Origin", ORIGIN)
                .param("username", "browser-fixture").param("password", "fixture-only-password")).andExpect(status().isForbidden());
        mvc.perform(post(API + "/api/local-auth/login").header("Origin", ORIGIN).cookie(token())
                .header("X-M4C-XSRF", "invalid")).andExpect(status().isForbidden());
    }
    @Test void anonymousWriteReturns401AndReadsRemainPublic() throws Exception {
        when(service.getWorkOrder(1L)).thenReturn(order());
        mvc.perform(get(API + "/api/work-orders/1")).andExpect(status().isOk());
        mvc.perform(post(API + "/api/work-orders").header("Origin", ORIGIN).contentType("application/json")
                .content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void authenticatedCreateRequiresActualCookieAndHeader() throws Exception {
        MockHttpSession session = login(); Cookie cookie = token();
        when(service.createWorkOrder(any())).thenReturn(order());
        mvc.perform(post(API + "/api/work-orders").session(session).header("Origin", ORIGIN).cookie(cookie)
                .header("X-M4C-XSRF", cookie.getValue()).contentType("application/json")
                .content("{\"title\":\"Fictional\",\"priority\":\"HIGH\",\"requesterEmail\":\"demo@example.com\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post(API + "/api/work-orders").session(session).header("Origin", ORIGIN)
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(patch(API + "/api/work-orders/1/status").session(session).header("Origin", ORIGIN).cookie(cookie)
                .header("X-M4C-XSRF", "invalid").contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void allMutationMethodsAndLogoutRequireCsrf() throws Exception {
        MockHttpSession session = login();
        for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), API + "/api/work-orders/1")
                    .session(session).header("Origin", ORIGIN).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post(API + "/api/local-auth/logout").session(session).header("Origin", ORIGIN)).andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
    }
    @Test void originHostAndBasicAreRejected() throws Exception {
        MockHttpSession session = login(); Cookie cookie = token();
        for (String origin : new String[]{"https://evil.example", "http://127.0.0.1:4300", "null"}) {
            mvc.perform(post(API + "/api/work-orders").session(session).cookie(cookie).header("X-M4C-XSRF", cookie.getValue())
                    .header("Origin", origin)).andExpect(status().isForbidden());
        }
        mvc.perform(post(API + "/api/work-orders").session(session).cookie(cookie).header("X-M4C-XSRF", cookie.getValue()))
                .andExpect(status().isForbidden());
        mvc.perform(get("http://public.example/api/local-auth/session")).andExpect(status().isForbidden());
        mvc.perform(options(API + "/api/work-orders").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get(API + "/api/local-auth/session").header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }
    @Test void logoutInvalidatesAndDeletesExactCookiePath() throws Exception {
        MockHttpSession session = login(); Cookie cookie = token();
        MvcResult logout = mvc.perform(post(API + "/api/local-auth/logout").session(session).header("Origin", ORIGIN)
                .cookie(cookie).header("X-M4C-XSRF", cookie.getValue())).andExpect(status().isNoContent()).andReturn();
        assertThat(session.isInvalid()).isTrue();
        Cookie cleared = logout.getResponse().getCookie("M4CSESSION");
        assertThat(cleared.getPath()).isEqualTo("/api"); assertThat(cleared.isHttpOnly()).isTrue(); assertThat(cleared.getMaxAge()).isZero();
        mvc.perform(get(API + "/api/local-auth/session")).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(patch(API + "/api/work-orders/1/status").header("Origin", ORIGIN)).andExpect(status().isUnauthorized());
    }
    @Test void expiredSessionNoLongerAuthorizesWrites() throws Exception {
        MockHttpSession session = login(); session.invalidate(); // Servlet expiry invalidates the session; real timeout is tested in Compose.
        mvc.perform(get(API + "/api/local-auth/session")).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(post(API + "/api/work-orders").header("Origin", ORIGIN)).andExpect(status().isUnauthorized());
    }
}
