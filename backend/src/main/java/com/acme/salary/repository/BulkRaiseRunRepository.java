package com.acme.salary.repository;

import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.enums.BulkRaiseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BulkRaiseRunRepository extends JpaRepository<BulkRaiseRun, Long> {

    /** Poller pickup: unfinished jobs, oldest first (QUEUED + crashed RUNNING). */
    List<BulkRaiseRun> findByStatusInOrderByIdAsc(Collection<BulkRaiseStatus> statuses);
}
