package com.acme.salary.repository;

import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaiseReviewItemRepository extends JpaRepository<RaiseReviewItem, Long> {

    Page<RaiseReviewItem> findByStatus(ReviewStatus status, Pageable pageable);
}
