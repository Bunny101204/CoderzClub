package com.coderzclub.service;

import com.coderzclub.model.Problem;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class ProblemNumericIdAllocator {
    private static final String COUNTER_ID = "problems.numericId";

    private final MongoTemplate mongoTemplate;

    public ProblemNumericIdAllocator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public int allocateNext() {
        ProblemNumericIdCounter counter = mongoTemplate.findAndModify(
            Query.query(Criteria.where("_id").is(COUNTER_ID)),
            new Update().inc("seq", 1),
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            ProblemNumericIdCounter.class,
            "counters");
        if (counter == null || counter.getSeq() == null || counter.getSeq() <= 0) {
            throw new IllegalStateException("Unable to allocate a positive problem numericId");
        }
        return counter.getSeq();
    }

    public static class ProblemNumericIdCounter {
        private String id;
        private Integer seq;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Integer getSeq() {
            return seq;
        }

        public void setSeq(Integer seq) {
            this.seq = seq;
        }
    }
}
