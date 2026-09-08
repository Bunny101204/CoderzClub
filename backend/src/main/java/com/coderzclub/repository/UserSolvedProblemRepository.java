package com.coderzclub.repository;

import com.coderzclub.model.UserSolvedProblem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSolvedProblemRepository extends MongoRepository<UserSolvedProblem, String> {
	java.util.Optional<UserSolvedProblem> findByUserIdAndProblemId(String userId, String problemId);
	java.util.List<UserSolvedProblem> findByRewardStatusIn(
		java.util.Collection<UserSolvedProblem.RewardStatus> statuses);
}
