package com.coderzclub.repository;

import com.coderzclub.model.UserStats;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserStatsRepository extends MongoRepository<UserStats, String> {}