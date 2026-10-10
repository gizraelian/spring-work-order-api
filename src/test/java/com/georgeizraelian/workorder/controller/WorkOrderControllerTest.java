package com.georgeizraelian.workorder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.georgeizraelian.workorder.config.SecurityConfig;
import com.georgeizraelian.workorder.dto.CreateWorkOrderRequest;
import com.georgeizraelian.workorder.dto.WorkOrderResponse;
import com.georgeizraelian.workorder.exception.GlobalExceptionHandler;
import com.georgeizraelian.workorder.exception.WorkOrderNotFoundException;
import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;
import com.georgeizraelian.workorder.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderController.class)
@ContextConfiguration(classes = {
        WorkOrderController.class, SecurityConfig.class, GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-password"
})
class WorkOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WorkOrderService workOrderService;

    @Test
    void publicGetListReturnsOk() throws Exception {
        when(workOrderService.getWorkOrders(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse())));

        mockMvc.perform(get("/api/work-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Replace failed network switch"));
    }

    @Test
    void publicGetKnownWorkOrderReturnsOk() throws Exception {
        when(workOrderService.getWorkOrder(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/work-orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getMissingWorkOrderReturnsNotFound() throws Exception {
        when(workOrderService.getWorkOrder(99L)).thenThrow(new WorkOrderNotFoundException(99L));

        mockMvc.perform(get("/api/work-orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Work order 99 was not found"));
    }

    @Test
    void authenticatedPostCreatesWorkOrder() throws Exception {
        CreateWorkOrderRequest request = validCreateRequest();
        when(workOrderService.createWorkOrder(any(CreateWorkOrderRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/work-orders")
                        .with(httpBasic("test-user", "test-password"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/work-orders/1"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser
    void invalidPostReturnsBadRequestWithFieldErrors() throws Exception {
        CreateWorkOrderRequest request = new CreateWorkOrderRequest(
                " ", null, null, "not-an-email", null);

        mockMvc.perform(post("/api/work-orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.priority").exists())
                .andExpect(jsonPath("$.validationErrors.requesterEmail").exists());
    }

    @Test
    void postWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void malformedStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/work-orders/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request contains an invalid value"));
    }

    @Test
    void wrongCredentialsRejectCreateAndStatus() throws Exception {
        mockMvc.perform(post("/api/work-orders").with(httpBasic("test-user", "wrong-password"))
                .contentType("application/json").content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/work-orders/1/status").with(httpBasic("test-user", "wrong-password"))
                .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/work-orders/1/status").contentType("application/json")
                .content("{\"status\":\"COMPLETED\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void nullStatusReturnsFieldError() throws Exception {
        mockMvc.perform(patch("/api/work-orders/1/status").contentType("application/json")
                .content("{\"status\":null}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.status").exists());
    }

    @Test
    @WithMockUser
    void statusUpdateReturnsResponse() throws Exception {
        when(workOrderService.updateStatus(1L, new com.georgeizraelian.workorder.dto.UpdateStatusRequest(WorkOrderStatus.COMPLETED))).thenReturn(sampleResponse(WorkOrderStatus.COMPLETED));
        mockMvc.perform(patch("/api/work-orders/1/status").contentType("application/json")
                .content("{\"status\":\"COMPLETED\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private CreateWorkOrderRequest validCreateRequest() {
        return new CreateWorkOrderRequest(
                "Replace failed network switch",
                "Connectivity is intermittent.",
                WorkOrderPriority.HIGH,
                "operations@example.com",
                LocalDate.of(2026, 9, 15));
    }

    private WorkOrderResponse sampleResponse() {
        return sampleResponse(WorkOrderStatus.OPEN);
    }

    private WorkOrderResponse sampleResponse(WorkOrderStatus orderStatus) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 9, 7, 10, 30);
        return new WorkOrderResponse(
                1L,
                "Replace failed network switch",
                "Connectivity is intermittent.",
                orderStatus,
                WorkOrderPriority.HIGH,
                "operations@example.com",
                LocalDate.of(2026, 9, 15),
                timestamp,
                timestamp);
    }
}
