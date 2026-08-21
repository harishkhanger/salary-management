package com.acme.salary.repository;

import com.acme.salary.entities.PayrollRun;
import com.acme.salary.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    /** Poller pickup: unfinished jobs, oldest first (QUEUED + crashed RUNNING). */
    List<PayrollRun> findByStatusInOrderByIdAsc(Collection<JobStatus> statuses);

    /** Payroll screen: org-wide runs still in flight, to mark their month PROCESSING. */
    List<PayrollRun> findByStatusInAndEmployeeIdIsNull(Collection<JobStatus> statuses);

    /** Analytics summary: the most recent completed run. */
    java.util.Optional<PayrollRun> findFirstByStatusOrderByCreatedAtDesc(JobStatus status);
}
