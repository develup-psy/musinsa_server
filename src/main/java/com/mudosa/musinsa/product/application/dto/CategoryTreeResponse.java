package com.mudosa.musinsa.product.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.HashMap;

@Value
@Builder(toBuilder = true)
@JsonDeserialize(builder = CategoryTreeResponse.CategoryTreeResponseBuilder.class)
public class CategoryTreeResponse {
	List<CategoryNode> categories;

	public static Map<Long, CategoryNode> flatten(CategoryTreeResponse tree) {
		Map<Long, CategoryNode> result = new HashMap<>();
		if (tree == null || tree.getCategories() == null || tree.getCategories().isEmpty()) {
			return result;
		}
		Queue<CategoryNode> queue = new LinkedList<>(tree.getCategories());
		while (!queue.isEmpty()) {
			CategoryNode node = queue.poll();
			result.put(node.getCategoryId(), node);
			if (node.getChildren() != null) {
				queue.addAll(node.getChildren());
			}
		}
		return result;
	}

	@Value
	@Builder(toBuilder = true)
	@JsonDeserialize(builder = CategoryNode.CategoryNodeBuilder.class)
	public static class CategoryNode {
		Long categoryId;
		String categoryName;
		String categoryPath;
		String imageUrl;
		List<CategoryNode> children;

		@JsonPOJOBuilder(withPrefix = "")
		public static class CategoryNodeBuilder {
		}
	}

	@JsonPOJOBuilder(withPrefix = "")
	public static class CategoryTreeResponseBuilder {
	}
}
