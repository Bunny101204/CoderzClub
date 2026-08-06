package com.coderzclub.repository;

import com.coderzclub.model.UserSolvedProblem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSolvedProblemRepository extends MongoRepository<UserSolvedProblem, String> {
}
