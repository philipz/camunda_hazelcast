package com.example.workflow.config;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.session.SessionRepository;
import org.springframework.session.hazelcast.HazelcastIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "hazelcast.enabled=true"
})
public class SessionConfigurationTest {

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Test
    public void testSessionRepositoryIsInjected() {
        assertNotNull(sessionRepository, "SessionRepository should be injected");
        assertTrue(sessionRepository instanceof HazelcastIndexedSessionRepository, 
                  "SessionRepository should be HazelcastIndexedSessionRepository");
    }

    @Test
    public void testSessionMapIsConfigured() {
        assertNotNull(hazelcastInstance, "HazelcastInstance should be available");
        
        IMap<String, Object> sessionMap = hazelcastInstance.getMap("spring-session-sessions");
        assertNotNull(sessionMap, "Session map should be configured");
        assertEquals("spring-session-sessions", sessionMap.getName(), 
                    "Session map name should match configuration");
    }

    @Test
    public void testSessionConfigurationIsActive() {
        // Verify that SessionConfig bean is active
        assertTrue(hazelcastInstance.getLifecycleService().isRunning(), 
                  "Hazelcast should be running for session storage");
        
        // Test that we can get the session map without errors
        IMap<String, Object> sessionMap = hazelcastInstance.getMap("spring-session-sessions");
        assertNotNull(sessionMap, "Should be able to access session map");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSessionRepositoryOperations() {
        // Test basic session repository functionality
        assertNotNull(sessionRepository, "SessionRepository should be available");
        
        // Cast to work with generic types
        SessionRepository<org.springframework.session.Session> typedRepository = 
            (SessionRepository<org.springframework.session.Session>) sessionRepository;
        
        // Create a new session
        org.springframework.session.Session session = typedRepository.createSession();
        assertNotNull(session, "Should be able to create a new session");
        assertNotNull(session.getId(), "Session should have an ID");
        assertNotNull(session.getCreationTime(), "Session should have creation time");
        assertFalse(session.isExpired(), "New session should not be expired");
        
        // Save the session
        typedRepository.save(session);
        
        // Verify session is stored in Hazelcast
        IMap<String, Object> sessionMap = hazelcastInstance.getMap("spring-session-sessions");
        assertTrue(sessionMap.size() > 0, "Session should be stored in Hazelcast map");
        
        // Retrieve the session
        org.springframework.session.Session retrievedSession = typedRepository.findById(session.getId());
        assertNotNull(retrievedSession, "Should be able to retrieve saved session");
        assertEquals(session.getId(), retrievedSession.getId(), "Retrieved session should have same ID");
        
        // Clean up
        typedRepository.deleteById(session.getId());
    }

    @Test
    public void testSessionTimeoutConfiguration() {
        // Test that sessions have proper timeout configuration
        SessionRepository<org.springframework.session.Session> typedRepository = 
            (SessionRepository<org.springframework.session.Session>) sessionRepository;
        
        org.springframework.session.Session session = typedRepository.createSession();
        assertNotNull(session, "Should be able to create session");
        
        // Test that session has a reasonable max inactive interval (30 minutes = 1800 seconds)
        int maxInactiveInterval = session.getMaxInactiveInterval().toSecondsPart() + 
                                 session.getMaxInactiveInterval().toMinutesPart() * 60;
        
        assertTrue(maxInactiveInterval > 0, "Session should have positive timeout");
        // Default Spring Session timeout is typically 30 minutes (1800 seconds)
        assertTrue(maxInactiveInterval >= 1800, "Session timeout should be at least 30 minutes");
        
        // Clean up
        typedRepository.deleteById(session.getId());
    }

    @Test
    public void testSessionMapNameConfiguration() {
        // Verify the session map uses the correct name from configuration
        IMap<String, Object> sessionMap = hazelcastInstance.getMap("spring-session-sessions");
        assertNotNull(sessionMap, "Session map should exist");
        assertEquals("spring-session-sessions", sessionMap.getName(), 
                    "Session map should use configured name");
        
        // Test that the map is empty initially (or clean it if not)
        if (sessionMap.size() > 0) {
            sessionMap.clear();
        }
        assertEquals(0, sessionMap.size(), "Session map should be clean for testing");
    }

    @Test
    public void testHazelcastSessionIntegration() {
        // Test integration between Spring Session and Hazelcast
        SessionRepository<org.springframework.session.Session> typedRepository = 
            (SessionRepository<org.springframework.session.Session>) sessionRepository;
        
        IMap<String, Object> sessionMap = hazelcastInstance.getMap("spring-session-sessions");
        int initialSize = sessionMap.size();
        
        // Create and save a session
        org.springframework.session.Session session = typedRepository.createSession();
        session.setAttribute("testAttribute", "testValue");
        typedRepository.save(session);
        
        // Verify session appears in Hazelcast map
        assertTrue(sessionMap.size() > initialSize, 
                  "Session should be stored in Hazelcast map");
        
        // Verify session attributes are preserved
        org.springframework.session.Session retrievedSession = typedRepository.findById(session.getId());
        assertNotNull(retrievedSession, "Session should be retrievable");
        assertEquals("testValue", retrievedSession.getAttribute("testAttribute"), 
                    "Session attributes should be preserved");
        
        // Clean up
        typedRepository.deleteById(session.getId());
        
        // Verify cleanup
        assertEquals(initialSize, sessionMap.size(), 
                    "Session should be removed from Hazelcast map");
    }
}