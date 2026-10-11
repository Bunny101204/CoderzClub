package com.coderzclub.model;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemAvailabilityTest {

    @Test
    void missingAvailabilityBehavesAsPublic() {
        Problem problem = new Problem();
        assertEquals(ProblemAvailability.PUBLIC, ProblemAvailability.of(problem));
        assertTrue(ProblemAvailability.isPublic(problem));
        assertFalse(ProblemAvailability.isContestOnly(problem));
    }

    @Test
    void blankAndNullParseAsPublic() {
        assertEquals(ProblemAvailability.PUBLIC, ProblemAvailability.fromValue(null));
        assertEquals(ProblemAvailability.PUBLIC, ProblemAvailability.fromValue(""));
        assertEquals(ProblemAvailability.PUBLIC, ProblemAvailability.fromValue("public"));
        assertEquals(ProblemAvailability.CONTEST_ONLY, ProblemAvailability.fromValue("contest_only"));
    }

    @Test
    void invalidValueIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ProblemAvailability.fromValue("HIDDEN"));
        assertTrue(ex.getMessage().contains("PUBLIC or CONTEST_ONLY"));
    }

    @Test
    void practiceListingCriteriaExcludesContestOnlyAndKeepsMissing() {
        Document query = ProblemAvailability.practiceListingCriteria().getCriteriaObject();
        Document ne = (Document) query.get("availability");
        assertEquals("CONTEST_ONLY", String.valueOf(ne.get("$ne")));
    }
}
