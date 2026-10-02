package edu.cit.escuzar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Shared identity for this running application process. */
@Component
public class AppInstance {
    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);
    private final String id = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();

    public AppInstance() {
        log.info("Application instance started: {} at {}", id, startedAt);
    }

    public String id() { return id; }
    public Instant startedAt() { return startedAt; }
    public long uptimeSeconds() { return java.time.Duration.between(startedAt, Instant.now()).toSeconds(); }
}
