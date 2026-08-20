package com.acme.salary.repository;

import com.acme.salary.entities.RaiseReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaiseReviewItemRepository extends JpaRepository<RaiseReviewItem, Long> {
}
