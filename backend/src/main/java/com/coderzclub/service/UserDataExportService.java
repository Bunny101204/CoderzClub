package com.coderzclub.service;

import com.coderzclub.dto.UserProfileResponse;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserDataExportService {
    private final MongoTemplate mongoTemplate;

    public UserDataExportService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Map<String, Object> export(User user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportedAt", new Date());
        payload.put("account", UserProfileResponse.from(user));

        Query submissionsQuery = Query.query(Criteria.where("userId").is(user.getId()));
        submissionsQuery.fields()
            .include("problemId").include("language").include("result").include("verdict")
            .include("createdAt").include("code").include("submissionJobId");
        List<Map<String, Object>> submissions = new ArrayList<>();
        for (Submission submission : mongoTemplate.find(submissionsQuery, Submission.class)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", submission.getId());
            row.put("problemId", submission.getProblemId());
            row.put("language", submission.getLanguage());
            row.put("result", submission.getResult());
            row.put("verdict", submission.getVerdict());
            row.put("createdAt", submission.getCreatedAt());
            row.put("code", submission.getCode());
            row.put("submissionJobId", submission.getSubmissionJobId());
            submissions.add(row);
        }
        payload.put("submissions", submissions);

        List<String> batchIds = mongoTemplate.find(
                Query.query(Criteria.where("userId").is(user.getId())),
                BatchMember.class)
            .stream()
            .map(BatchMember::getBatchId)
            .toList();
        payload.put("batchMemberships", batchIds);
        return payload;
    }
}
