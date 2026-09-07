package com.georgeizraelian.workorder.repository;

import com.georgeizraelian.workorder.model.WorkOrder;
import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class WorkOrderRepositoryTest {

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Test
    void filtersByStatusAndPriorityUsingH2() {
        workOrderRepository.save(new WorkOrder(
                "Replace failed network switch",
                "Connectivity is intermittent.",
                WorkOrderStatus.OPEN,
                WorkOrderPriority.HIGH,
                "operations@example.com",
                LocalDate.of(2026, 9, 15)));
        workOrderRepository.save(new WorkOrder(
                "Repair barcode scanner",
                null,
                WorkOrderStatus.COMPLETED,
                WorkOrderPriority.LOW,
                "support@example.com",
                null));

        var result = workOrderRepository.findByStatusAndPriority(
                WorkOrderStatus.OPEN, WorkOrderPriority.HIGH, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getTitle())
                .isEqualTo("Replace failed network switch");
    }
}
