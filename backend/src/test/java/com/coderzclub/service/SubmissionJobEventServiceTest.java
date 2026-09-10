package com.coderzclub.service;

import com.coderzclub.dto.SubmissionJobEvent;
import com.coderzclub.model.SubmissionJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionJobEventServiceTest {
    @Test
    void eventPayloadContainsNoTestcaseData() throws Exception {
        SubmissionJob job = new SubmissionJob();
        job.setId("job-1");
        job.setStatus(SubmissionJob.JobStatus.COMPLETED);
        job.setFinalResult("ACCEPTED");
        job.setErrorMessage("safe error");
        SubmissionJobEvent event = SubmissionJobEvent.from(job);

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
        assertFalse(json.contains("input"));
        assertFalse(json.contains("expected"));
        assertFalse(json.contains("secret"));
        assertEquals("job-1", event.getJobId());
        assertEquals("ACCEPTED", event.getResult());
        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void nonFinalEventOmitsFinalFields() {
        SubmissionJob job = new SubmissionJob();
        job.setId("job-1");
        job.setStatus(SubmissionJob.JobStatus.RUNNING);
        job.setFinalResult("ACCEPTED");
        job.setTotalRuntime(10L);

        SubmissionJobEvent event = SubmissionJobEvent.from(job);

        assertNull(event.getResult());
        assertNull(event.getRuntime());
        assertNull(event.getMemory());
        assertEquals(0, event.getCompletedTests());
        assertEquals(0, event.getTotalTests());
        assertEquals(0, event.getAttemptCount());
    }
}
