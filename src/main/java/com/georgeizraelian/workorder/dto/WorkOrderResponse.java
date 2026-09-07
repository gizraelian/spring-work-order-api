package com.georgeizraelian.workorder.dto;

import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WorkOrderResponse(
        Long id,
        String title,
        String description,
        WorkOrderStatus status,
        WorkOrderPriority priority,
        String requesterEmail,
        LocalDate dueDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
