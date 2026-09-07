package com.georgeizraelian.workorder.dto;

import com.georgeizraelian.workorder.model.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull WorkOrderStatus status) {
}
