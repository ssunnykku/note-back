package com.sun.note.repository;

import com.sun.note.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long>, CategoryQueryRepository {

} 