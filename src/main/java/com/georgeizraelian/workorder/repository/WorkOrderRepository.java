package com.georgeizraelian.workorder.repository;

import com.georgeizraelian.workorder.model.WorkOrder;
import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Page<WorkOrder> findByStatus(WorkOrderStatus status, Pageable pageable);

    Page<WorkOrder> findByPriority(WorkOrderPriority priority, Pageable pageable);

    Page<WorkOrder> findByStatusAndPriority(WorkOrderStatus status, WorkOrderPriority priority, Pageable pageable);
}
