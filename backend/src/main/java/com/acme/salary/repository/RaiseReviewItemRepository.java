package com.acme.salary.repository;

import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RaiseReviewItemRepository extends JpaRepository<RaiseReviewItem, Long> {

    Page<RaiseReviewItem> findByStatus(ReviewStatus status, Pageable pageable);

    /** Resume support: employees this bulk run has already parked for review. */
    @Query("SELECT r.employeeId FROM RaiseReviewItem r WHERE r.bulkRaiseRunId = :runId")
    List<Long> findEmployeeIdsByRun(@Param("runId") Long runId);
}
