package com.coderzclub.service;

import com.coderzclub.dto.ContestRunResponse;
import com.coderzclub.model.ContestRunJob;
import com.coderzclub.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContestRunQueryServiceTest {
    @Test
    void ownerCanReadOwnRun() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ContestRunJob job = job("run-1", "u1");
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        ContestRunQueryService service = new ContestRunQueryService(mongo);
        ContestRunResponse response = service.getVisible("run-1", user("u1", "USER"),
            auth("alice", "ROLE_USER"));
        assertEquals("run-1", response.getRunId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(1, response.getResults().size());
        assertEquals("1", response.getResults().get(0).getInput());
    }

    @Test
    void otherUserCannotReadRun() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job("run-1", "u1"));
        ContestRunQueryService service = new ContestRunQueryService(mongo);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.getVisible("run-1", user("u2", "USER"), auth("bob", "ROLE_USER")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void adminCanInspect() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job("run-1", "u1"));
        ContestRunQueryService service = new ContestRunQueryService(mongo);
        ContestRunResponse response = service.getVisible("run-1", user("admin", "ADMIN"),
            auth("admin", "ROLE_ADMIN"));
        assertEquals("run-1", response.getRunId());
    }

    private static ContestRunJob job(String id, String userId) {
        ContestRunJob job = new ContestRunJob();
        job.setId(id);
        job.setUserId(userId);
        job.setStatus(ContestRunJob.Status.COMPLETED);
        ContestRunJob.PublicTestResult result = new ContestRunJob.PublicTestResult();
        result.setInput("1");
        result.setExpectedOutput("1");
        result.setActualOutput("1");
        result.setPassed(true);
        job.setPublicResults(List.of(result));
        job.setCompletedTests(1);
        job.setTotalTests(1);
        return job;
    }

    private static User user(String id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setRole(role);
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private static UsernamePasswordAuthenticationToken auth(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, "n", List.of(new SimpleGrantedAuthority(role)));
    }
}
