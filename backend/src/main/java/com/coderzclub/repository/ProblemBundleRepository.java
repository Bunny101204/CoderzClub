package com.coderzclub.repository;

import com.coderzclub.model.ProblemBundle;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProblemBundleRepository extends MongoRepository<ProblemBundle, String> {
    
    List<ProblemBundle> findByIsActiveTrue();
    
    List<ProblemBundle> findByDifficultyAndIsActiveTrue(String difficulty);
    
    List<ProblemBundle> findByCategoryAndIsActiveTrue(String category);
    
    List<ProblemBundle> findByIsPremiumFalseAndIsActiveTrue();
    
    List<ProblemBundle> findByCreatedBy(String createdBy);
}






