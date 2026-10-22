package com.coderzclub.controller;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemSearchCriteriaTest {

    @Test
    void numericSearchMatchesHumanFacingIdAndTitlePrefix() {
        Document query = ProblemSearchCriteria.forTerm("26").getCriteriaObject();
        @SuppressWarnings("unchecked")
        List<Document> orClauses = (List<Document>) query.get("$or");
        Document numericClause = orClauses.stream()
            .filter(clause -> clause.containsKey("numericId"))
            .findFirst()
            .orElseThrow();
        assertInstanceOf(Integer.class, numericClause.get("numericId"));
        assertEquals(26, numericClause.get("numericId"));
        assertFalse(numericClause.containsKey("$regex"));
        assertTrue(orClauses.stream().anyMatch(clause -> clause.containsKey("title")));
        assertTrue(orClauses.stream().anyMatch(clause -> clause.containsKey("_id")));
        assertEquals(3, orClauses.size());
    }

    @Test
    void titleSearchDoesNotRequireNumericIdEquality() {
        Document query = ProblemSearchCriteria.forTerm("array").getCriteriaObject();
        @SuppressWarnings("unchecked")
        List<Document> orClauses = (List<Document>) query.get("$or");
        assertTrue(orClauses.stream().anyMatch(clause -> clause.containsKey("title")));
        assertTrue(orClauses.stream().anyMatch(clause -> clause.containsKey("_id")));
        assertFalse(orClauses.stream().anyMatch(clause -> clause.containsKey("numericId")));
        assertEquals(2, orClauses.size());
    }

    @Test
    void regexMetacharactersAreQuotedAsLiteralPrefix() {
        Document query = ProblemSearchCriteria.forTerm("arr.*").getCriteriaObject();
        @SuppressWarnings("unchecked")
        List<Document> orClauses = (List<Document>) query.get("$or");
        Document title = orClauses.stream()
            .filter(clause -> clause.containsKey("title"))
            .findFirst()
            .orElseThrow();
        Object titleValue = title.get("title");
        String pattern = titleValue instanceof Document
            ? ((Document) titleValue).getString("$regex")
            : String.valueOf(titleValue);
        assertTrue(pattern.contains(Pattern.quote("arr.*")));
        assertFalse(orClauses.stream().anyMatch(clause -> clause.containsKey("numericId")));
    }

    @Test
    void oversizedDigitStringDoesNotThrowOrCompareNumericId() {
        Document query = assertDoesNotThrow(
            () -> ProblemSearchCriteria.forTerm("99999999999999999999").getCriteriaObject());
        @SuppressWarnings("unchecked")
        List<Document> orClauses = (List<Document>) query.get("$or");
        assertFalse(orClauses.stream().anyMatch(clause -> clause.containsKey("numericId")));
        assertEquals(2, orClauses.size());
    }
}
