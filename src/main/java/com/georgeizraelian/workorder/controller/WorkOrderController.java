package com.georgeizraelian.workorder.controller;

import com.georgeizraelian.workorder.dto.CreateWorkOrderRequest;
import com.georgeizraelian.workorder.dto.UpdateStatusRequest;
import com.georgeizraelian.workorder.dto.UpdateWorkOrderRequest;
import com.georgeizraelian.workorder.dto.WorkOrderResponse;
import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;
import com.georgeizraelian.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping
    public Page<WorkOrderResponse> getWorkOrders(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) WorkOrderPriority priority,
            @PageableDefault(size = 20) Pageable pageable) {
        return workOrderService.getWorkOrders(status, priority, pageable);
    }

    @GetMapping("/{id}")
    public WorkOrderResponse getWorkOrder(@PathVariable Long id) {
        return workOrderService.getWorkOrder(id);
    }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> createWorkOrder(
            @Valid @RequestBody CreateWorkOrderRequest request) {
        WorkOrderResponse created = workOrderService.createWorkOrder(request);
        return ResponseEntity.created(URI.create("/api/work-orders/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public WorkOrderResponse updateWorkOrder(
            @PathVariable Long id, @Valid @RequestBody UpdateWorkOrderRequest request) {
        return workOrderService.updateWorkOrder(id, request);
    }

    @PatchMapping("/{id}/status")
    public WorkOrderResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return workOrderService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkOrder(@PathVariable Long id) {
        workOrderService.deleteWorkOrder(id);
        return ResponseEntity.noContent().build();
    }
}
