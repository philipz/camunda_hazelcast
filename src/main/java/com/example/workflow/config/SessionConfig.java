package com.example.workflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;

@Configuration
@ConditionalOnProperty(prefix = "hazelcast", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableHazelcastHttpSession(sessionMapName = "spring-session-sessions")
public class SessionConfig {
}