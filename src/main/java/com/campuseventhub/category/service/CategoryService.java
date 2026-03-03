package com.campuseventhub.category.service;

import com.campuseventhub.category.dto.CategoryRequest;
import com.campuseventhub.category.dto.CategoryResponse;
import com.campuseventhub.category.entity.Category;
import com.campuseventhub.category.mapper.CategoryMapper;
import com.campuseventhub.category.repository.CategoryRepository;
import com.campuseventhub.common.exception.ConflictException;
import com.campuseventhub.common.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Cacheable("categories")
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }

    public CategoryResponse getById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        return CategoryMapper.toResponse(category);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse create(CategoryRequest request) {
        categoryRepository.findByNameIgnoreCase(request.getName()).ifPresent(c -> {
            throw new ConflictException("Category already exists with name: " + request.getName());
        });

        Category category = new Category();
        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());

        return CategoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        categoryRepository.findByNameIgnoreCase(request.getName())
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw new ConflictException("Category already exists with name: " + request.getName());
                });

        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());

        return CategoryMapper.toResponse(category);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        categoryRepository.delete(category);
    }
}
