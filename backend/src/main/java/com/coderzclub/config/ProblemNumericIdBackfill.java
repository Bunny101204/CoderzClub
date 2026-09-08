package com.coderzclub.config;

import com.coderzclub.model.Problem;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProblemNumericIdBackfill implements CommandLineRunner {
    private final MongoTemplate mongoTemplate;

    public ProblemNumericIdBackfill(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        List<Problem> missing = mongoTemplate.find(
            Query.query(Criteria.where("numericId").exists(false)).with(Sort.by(Sort.Direction.ASC, "_id")),
            Problem.class);
        Problem highest = mongoTemplate.findOne(
            Query.query(Criteria.where("numericId").exists(true)).with(Sort.by(Sort.Direction.DESC, "numericId")),
            Problem.class);
        int next = highest == null || highest.getNumericId() == null ? 0 : highest.getNumericId();
        for (Problem problem : missing) {
            int numericId = parseId(problem.getId(), ++next);
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(problem.getId()).and("numericId").exists(false)),
                new Update().set("numericId", numericId), Problem.class);
            next = Math.max(next, numericId);
        }
    }

    private int parseId(String id, int fallback) {
        try {
            int parsed = Integer.parseInt(id);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}