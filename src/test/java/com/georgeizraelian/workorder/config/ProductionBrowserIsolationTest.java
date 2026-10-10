package com.georgeizraelian.workorder.config;
import com.georgeizraelian.workorder.controller.LocalSessionController;
import com.georgeizraelian.workorder.controller.WorkOrderController;
import com.georgeizraelian.workorder.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({LocalSessionController.class, WorkOrderController.class})
@Import({SecurityConfig.class, LocalBrowserSecurityConfig.class, EnvironmentSafetyConfig.class})
@ActiveProfiles("prod")
@TestPropertySource(properties={"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/fixture",
 "spring.datasource.username=fixture", "spring.datasource.password=fixture-only",
 "app.security.username=fixture", "app.security.password=fixture-only"})
class ProductionBrowserIsolationTest {
 @Autowired MockMvc mvc;
 @Autowired org.springframework.context.ApplicationContext context;
 @MockitoBean WorkOrderService service;
 @Test void productionDoesNotRegisterBrowserAuthOrSessionChain() throws Exception {
  assertThat(context.getBeansOfType(LocalSessionController.class)).isEmpty();
  assertThat(context.getBeansOfType(LocalBrowserSecurityConfig.class)).isEmpty();
  for (String endpoint : new String[]{"csrf", "session", "login", "logout"}) {
   mvc.perform(get("/api/local-auth/"+endpoint).with(httpBasic("fixture","fixture-only"))).andExpect(status().isForbidden());
   mvc.perform(post("/api/local-auth/"+endpoint).with(httpBasic("fixture","fixture-only"))).andExpect(status().isForbidden());
  }
 }
}
