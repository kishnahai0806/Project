package com.campuseventhub.category.mapper;

import com.campuseventhub.category.dto.CategoryResponse;
import com.campuseventhub.category.entity.Category;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
