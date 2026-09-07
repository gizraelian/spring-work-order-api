package com.georgeizraelian.workorder.service;

import com.georgeizraelian.workorder.dto.CreateWorkOrderRequest;
import com.georgeizraelian.workorder.dto.UpdateStatusRequest;
import com.georgeizraelian.workorder.exception.WorkOrderNotFoundException;
import com.georgeizraelian.workorder.model.WorkOrder;
import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;
import com.georgeizraelian.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    private WorkOrderService workOrderService;

    @BeforeEach
    void setUp() {
        workOrderService = new WorkOrderService(workOrderRepository);
    }

    @Test
    void creatingWorkOrderDefaultsToOpen() {
        CreateWorkOrderRequest request = new CreateWorkOrderRequest(
                "Replace failed network switch",
                "Connectivity is intermittent.",
                WorkOrderPriority.HIGH,
                "operations@example.com",
                LocalDate.of(2026, 9, 15));
        when(workOrderRepository.save(org.mockito.ArgumentMatchers.any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = workOrderService.createWorkOrder(request);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.OPEN);
        assertThat(response.title()).isEqualTo(request.title());
    }

    @Test
    void retrievingMissingIdThrowsNotFound() {
        when(workOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workOrderService.getWorkOrder(99L))
                .isInstanceOf(WorkOrderNotFoundException.class)
                .hasMessage("Work order 99 was not found");
    }

    @Test
    void statusUpdateChangesOnlyStatus() {
        WorkOrder workOrder = sampleWorkOrder();
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(workOrder)).thenReturn(workOrder);

        var response = workOrderService.updateStatus(
                1L, new UpdateStatusRequest(WorkOrderStatus.COMPLETED));

        assertThat(response.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(response.title()).isEqualTo("Investigate printer connectivity");
    }

    @Test
    void deletionLoadsAndDeletesExistingWorkOrder() {
        WorkOrder workOrder = sampleWorkOrder();
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(workOrder));

        workOrderService.deleteWorkOrder(1L);

        verify(workOrderRepository).delete(workOrder);
    }

    private WorkOrder sampleWorkOrder() {
        return new WorkOrder(
                "Investigate printer connectivity",
                "The printer drops its connection.",
                WorkOrderStatus.IN_PROGRESS,
                WorkOrderPriority.MEDIUM,
                "support@example.com",
                LocalDate.of(2026, 9, 18));
    }
}
