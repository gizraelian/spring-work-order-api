package com.georgeizraelian.workorder.service;

import com.georgeizraelian.workorder.dto.CreateWorkOrderRequest;
import com.georgeizraelian.workorder.dto.UpdateStatusRequest;
import com.georgeizraelian.workorder.dto.UpdateWorkOrderRequest;
import com.georgeizraelian.workorder.dto.WorkOrderResponse;
import com.georgeizraelian.workorder.exception.WorkOrderNotFoundException;
import com.georgeizraelian.workorder.model.WorkOrder;
import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;
import com.georgeizraelian.workorder.repository.WorkOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;

    public WorkOrderService(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Page<WorkOrderResponse> getWorkOrders(
            WorkOrderStatus status, WorkOrderPriority priority, Pageable pageable) {
        Page<WorkOrder> workOrders;

        if (status != null && priority != null) {
            workOrders = workOrderRepository.findByStatusAndPriority(status, priority, pageable);
        } else if (status != null) {
            workOrders = workOrderRepository.findByStatus(status, pageable);
        } else if (priority != null) {
            workOrders = workOrderRepository.findByPriority(priority, pageable);
        } else {
            workOrders = workOrderRepository.findAll(pageable);
        }

        return workOrders.map(this::toResponse);
    }

    public WorkOrderResponse getWorkOrder(Long id) {
        return toResponse(findWorkOrder(id));
    }

    @Transactional
    public WorkOrderResponse createWorkOrder(CreateWorkOrderRequest request) {
        // New work orders always begin in the OPEN state.
        WorkOrder workOrder = new WorkOrder(
                request.title(),
                request.description(),
                WorkOrderStatus.OPEN,
                request.priority(),
                request.requesterEmail(),
                request.dueDate()
        );
        return toResponse(workOrderRepository.save(workOrder));
    }

    @Transactional
    public WorkOrderResponse updateWorkOrder(Long id, UpdateWorkOrderRequest request) {
        WorkOrder workOrder = findWorkOrder(id);
        workOrder.setTitle(request.title());
        workOrder.setDescription(request.description());
        workOrder.setPriority(request.priority());
        workOrder.setRequesterEmail(request.requesterEmail());
        workOrder.setDueDate(request.dueDate());
        return toResponse(workOrderRepository.saveAndFlush(workOrder));
    }

    @Transactional
    public WorkOrderResponse updateStatus(Long id, UpdateStatusRequest request) {
        WorkOrder workOrder = findWorkOrder(id);
        workOrder.setStatus(request.status());
        return toResponse(workOrderRepository.saveAndFlush(workOrder));
    }

    @Transactional
    public void deleteWorkOrder(Long id) {
        WorkOrder workOrder = findWorkOrder(id);
        workOrderRepository.delete(workOrder);
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));
    }

    private WorkOrderResponse toResponse(WorkOrder workOrder) {
        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getTitle(),
                workOrder.getDescription(),
                workOrder.getStatus(),
                workOrder.getPriority(),
                workOrder.getRequesterEmail(),
                workOrder.getDueDate(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt()
        );
    }
}
