package com.georgeizraelian.workorder.config;

import com.georgeizraelian.workorder.model.WorkOrder;
import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;
import com.georgeizraelian.workorder.repository.WorkOrderRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;

@Configuration
public class DemoDataConfig {

    @Bean
    CommandLineRunner loadDemoWorkOrders(WorkOrderRepository repository) {
        return args -> {
            // Preserve user-created records and avoid duplicating demo data on restart.
            if (repository.count() > 0) {
                return;
            }

            LocalDate today = LocalDate.now();
            repository.saveAll(List.of(
                    new WorkOrder(
                            "Replace failed network switch",
                            "Intermittent connectivity reported at the inspection workstation.",
                            WorkOrderStatus.OPEN,
                            WorkOrderPriority.HIGH,
                            "operations@example.com",
                            today.plusDays(3)),
                    new WorkOrder(
                            "Investigate printer connectivity",
                            "The shipping printer drops its network connection during longer jobs.",
                            WorkOrderStatus.IN_PROGRESS,
                            WorkOrderPriority.MEDIUM,
                            "support@example.com",
                            today.plusDays(5)),
                    new WorkOrder(
                            "Update inspection workstation software",
                            "Install the approved software update after the current inspection cycle.",
                            WorkOrderStatus.OPEN,
                            WorkOrderPriority.MEDIUM,
                            "operations@example.com",
                            today.plusDays(10)),
                    new WorkOrder(
                            "Repair barcode scanner",
                            "Replace the worn trigger and confirm scans register correctly.",
                            WorkOrderStatus.COMPLETED,
                            WorkOrderPriority.LOW,
                            "support@example.com",
                            null)
            ));
        };
    }
}
