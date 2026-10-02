package com.coderzclub.controller;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ProblemSearchCriteria {
    private ProblemSearchCriteria() {}

    static Criteria forTerm(String search) {
        String escapedSearch = Pattern.quote(search);
        List<Criteria> searchClauses = new ArrayList<>();
        searchClauses.add(Criteria.where("title").regex("^" + escapedSearch, "i"));
        searchClauses.add(Criteria.where("_id").regex("^" + escapedSearch, "i"));
        if (search.chars().allMatch(Character::isDigit)) {
            try {
                searchClauses.add(Criteria.where("numericId").is(Integer.parseInt(search)));
            } catch (NumberFormatException ignored) {
                // Numeric-looking but not a valid Integer; keep title/_id prefix match.
            }
        }
        return new Criteria().orOperator(searchClauses.toArray(Criteria[]::new));
    }
}
