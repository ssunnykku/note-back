package com.sun.note.repository;

import com.sun.note.domain.entity.Category;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long>, CategoryQueryRepository {
    Optional<Category> findByIdAndUserId(Long id, UUID userId);
} 