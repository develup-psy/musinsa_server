package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    /**
     * 카테고리 이름으로 카테고리를 찾습니다 (인덱스 활용을 위한 쿼리)
     */
    Optional<Category> findByCategoryName(String categoryName);
    
    /**
     * 효율적인 경로 검색:
     * 1. categoryPath에서 마지막 부분(자식 카테고리 이름) 추출
     * 2. 자식 이름으로 DB에서 후보 검색 (인덱스 활용)
     * 3. 후보들의 buildPath() 결과와 입력된 경로 비교
     * 
     * @param path 검색할 카테고리 경로 (예: "상의>카디건")
     * @return 일치하는 카테고리, 없으면 null
     */
    default Category findByPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        
        // 1. 경로에서 마지막 부분(자식 카테고리 이름) 추출
        String[] pathParts = path.split(">");
        String childCategoryName = pathParts[pathParts.length - 1].trim();
        
        // 2. 자식 이름으로 카테고리 검색 (DB 인덱스 활용)
        Optional<Category> categoryOpt = findByCategoryName(childCategoryName);
        
        if (!categoryOpt.isPresent()) {
            return null;
        }
        
        // 3. buildPath() 결과와 입력된 경로가 일치하는지 확인
        Category category = categoryOpt.get();
        try {
            String builtPath = category.buildPath();
            return path.equals(builtPath) ? category : null;
        } catch (Exception e) {
            // buildPath() 중 예외 발생 시 null 반환
            return null;
        }
    }
    
    /**
     * 카테고리 경로가 유효한지 검증합니다.
     * 
     * @param path 검증할 카테고리 경로
     * @return 유효하면 true, 아니면 false
     */
    default boolean isValidPath(String path) {
        return findByPath(path) != null;
    }

    @Query("select c from Category c left join fetch c.parent")
    List<Category> findAllWithParent();
}
