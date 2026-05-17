package com.team7.eventticketing.ticket.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cassandra.autoconfigure.CqlSessionBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
public class CassandraKeyspaceConfig {

    private static final Logger log = LoggerFactory.getLogger(CassandraKeyspaceConfig.class);

    @Value("${spring.cassandra.contact-points:cassandra}")
    private String contactPoints;

    @Value("${spring.cassandra.port:9042}")
    private int port;

    @Value("${spring.cassandra.local-datacenter:datacenter1}")
    private String localDatacenter;

    @Value("${spring.cassandra.keyspace-name:eventticketingks}")
    private String keyspaceName;

    /**
     * Runs before Spring Data Cassandra builds its CqlSession.
     * Opens a keyspace-less bootstrap session, creates the keyspace if absent, then closes it.
     * Exceptions are caught so Cassandra remains a soft dependency — the service starts even
     * if Cassandra is temporarily unreachable, and only scan-related endpoints will fail.
     */
    @Bean
    public CqlSessionBuilderCustomizer ensureKeyspaceExists() {
        return builder -> {
            try (CqlSession bootstrap = CqlSession.builder()
                    .addContactPoint(new InetSocketAddress(contactPoints, port))
                    .withLocalDatacenter(localDatacenter)
                    .build()) {
                bootstrap.execute(String.format(
                        "CREATE KEYSPACE IF NOT EXISTS %s " +
                        "WITH replication = {'class':'SimpleStrategy','replication_factor':1}",
                        keyspaceName));
                log.info("Cassandra keyspace '{}' is ready.", keyspaceName);
            } catch (Exception e) {
                log.warn("Cassandra unavailable at startup; keyspace creation skipped: {}", e.getMessage());
            }
        };
    }
}
