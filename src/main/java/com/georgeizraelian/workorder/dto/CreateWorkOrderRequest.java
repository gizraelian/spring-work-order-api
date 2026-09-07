package com.georgeizraelian.workorder.dto;

import com.georgeizraelian.workorder.model.WorkOrderPriority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateWorkOrderRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @NotNull WorkOrderPriority priority,
        @NotBlank @Email @Size(max = 254) String requesterEmail,
        LocalDate dueDate
) {
}
