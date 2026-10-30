package com.coderzclub.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Local-only check that startup index creation refuses a weaker or missing
 * index when the application user cannot create indexes. Requires the
 * isolated auth database on 127.0.0.1:27019.
 */
@EnabledIfEnvironmentVariable(named = "CODERZCLUB_INDEX_AUTH", matches = "1")
class IndexPrivilegeIT {
    private static final String ADMIN_URI =
        "mongodb://localroot:localroot-only@127.0.0.1:27019/admin?authSource=admin";
    private static final String APP_URI =
        "mongodb://noindex:noindex-local-only@127.0.0.1:27019/coderzclub_idx?authSource=coderzclub_idx";

    @Test
    void missingOrConflictingIndexWithoutCreatePrivilegeStopsStartup() {
        try (MongoClient admin = MongoClients.create(ADMIN_URI)) {
            admin.getDatabase("coderzclub_idx").getCollection("mutation_idempotency").drop();
            admin.getDatabase("coderzclub_idx").getCollection("submission_jobs").drop();
        }
        MutationIdempotencyService restricted = restrictedService();
        IllegalStateException missing = assertThrows(IllegalStateException.class, restricted::ensureIndexes);
        assertTrue(missing.getMessage().contains("cannot create indexes")
            || missing.getMessage().contains("conflicts"));

        try (MongoClient admin = MongoClients.create(ADMIN_URI)) {
            MongoTemplate adminTemplate = new MongoTemplate(admin, "coderzclub_idx");
            new MutationIdempotencyService(adminTemplate, null, Clock.systemUTC()).ensureIndexes();
        }
        assertDoesNotThrow(restricted::ensureIndexes);
        assertDoesNotThrow(restricted::ensureIndexes);

        try (MongoClient admin = MongoClients.create(ADMIN_URI)) {
            admin.getDatabase("coderzclub_idx").getCollection("mutation_idempotency")
                .dropIndex(MutationIdempotencyService.OPERATION_INDEX);
            admin.getDatabase("coderzclub_idx").getCollection("mutation_idempotency")
                .createIndex(new org.bson.Document("scope", 1),
                    new com.mongodb.client.model.IndexOptions().name(MutationIdempotencyService.OPERATION_INDEX));
        }
        IllegalStateException conflict = assertThrows(IllegalStateException.class, restricted::ensureIndexes);
        assertTrue(conflict.getMessage().contains("conflict") || conflict.getMessage().contains("cannot create indexes"));
    }

    private static MutationIdempotencyService restrictedService() {
        MongoClient client = MongoClients.create(APP_URI);
        return new MutationIdempotencyService(new MongoTemplate(client, "coderzclub_idx"), null, Clock.systemUTC());
    }
}
