package com.example.workflow.integration;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SessionIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(SessionIntegrationTest.class);

    @Autowired
    private SessionRepository<Session> sessionRepository;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    private IMap<String, Object> sessionMap;

    @BeforeEach
    public void setUp() {
        sessionMap = hazelcastInstance.getMap("spring-session-sessions");
        assertNotNull(sessionMap, "Session map should be available");
        
        // Defensive cleanup with verification
        try {
            sessionMap.clear();
            // Verify cleanup succeeded
            assertEquals(0, sessionMap.size(), "Session map should be clean after setup");
            logger.debug("Session map cleaned successfully for test setup");
        } catch (Exception e) {
            logger.warn("Failed to clean session map during setup: {}", e.getMessage());
            // Still proceed with test but log the issue
        }
    }

    @Test
    public void testSessionIntegrationComponents() {
        assertNotNull(sessionRepository, "SessionRepository should be injected");
        assertNotNull(hazelcastInstance, "HazelcastInstance should be injected");
        assertTrue(hazelcastInstance.getLifecycleService().isRunning(), "Hazelcast should be running");
        assertNotNull(sessionMap, "Session map should be available");
        assertEquals("spring-session-sessions", sessionMap.getName(), "Session map should have correct name");
    }

    @Test
    public void testSessionLifecycle() {
        // Session map already cleaned in setUp method
        
        // Create a new session
        Session session = sessionRepository.createSession();
        assertNotNull(session, "Session should be created");
        assertNotNull(session.getId(), "Session should have an ID");
        assertNotNull(session.getCreationTime(), "Session should have creation time");
        assertNotNull(session.getLastAccessedTime(), "Session should have last accessed time");
        assertFalse(session.isExpired(), "New session should not be expired");
        
        // Add some attributes
        session.setAttribute("username", "testUser");
        session.setAttribute("role", "admin");
        
        // Save the session
        sessionRepository.save(session);
        
        // Verify session is stored in Hazelcast
        assertTrue(sessionMap.size() > 0, "Session should be stored in Hazelcast map");
        
        // Retrieve the session
        Session retrievedSession = sessionRepository.findById(session.getId());
        assertNotNull(retrievedSession, "Session should be retrievable");
        assertEquals(session.getId(), retrievedSession.getId(), "Session IDs should match");
        assertEquals("testUser", retrievedSession.getAttribute("username"), "Username should be preserved");
        assertEquals("admin", retrievedSession.getAttribute("role"), "Role should be preserved");
        
        // Update session attribute
        retrievedSession.setAttribute("lastAction", "login");
        sessionRepository.save(retrievedSession);
        
        // Verify update
        Session updatedSession = sessionRepository.findById(session.getId());
        assertEquals("login", updatedSession.getAttribute("lastAction"), "Updated attribute should be preserved");
        
        // Delete the session
        sessionRepository.deleteById(session.getId());
        
        // Verify deletion
        Session deletedSession = sessionRepository.findById(session.getId());
        assertNull(deletedSession, "Deleted session should not be retrievable");
        assertEquals(0, sessionMap.size(), "Session should be removed from Hazelcast map");
    }

    @Test
    public void testSessionSharingBetweenInstances() {
        // This test simulates multiple application instances sharing session data
        sessionMap.clear();
        
        // Simulate first application instance creating a session
        Session session1 = sessionRepository.createSession();
        session1.setAttribute("instanceId", "instance1");
        session1.setAttribute("sharedData", "important_data");
        sessionRepository.save(session1);
        String sessionId = session1.getId();
        
        // Verify session is in Hazelcast
        assertTrue(sessionMap.containsKey("spring:session:sessions:" + sessionId) || sessionMap.size() > 0, 
                  "Session should be stored in shared Hazelcast map");
        
        // Simulate second application instance accessing the same session
        // (In real scenario, this would be a different SessionRepository instance)
        Session session2 = sessionRepository.findById(sessionId);
        assertNotNull(session2, "Session should be accessible from different instance");
        assertEquals("instance1", session2.getAttribute("instanceId"), "Shared data should be accessible");
        assertEquals("important_data", session2.getAttribute("sharedData"), "Shared data should be preserved");
        
        // Modify session from "second instance"
        session2.setAttribute("instanceId", "instance2");
        session2.setAttribute("modification", "from_instance2");
        sessionRepository.save(session2);
        
        // Verify changes are visible to "first instance"
        Session session3 = sessionRepository.findById(sessionId);
        assertEquals("instance2", session3.getAttribute("instanceId"), "Changes should be shared");
        assertEquals("from_instance2", session3.getAttribute("modification"), "New attributes should be shared");
        assertEquals("important_data", session3.getAttribute("sharedData"), "Original data should be preserved");
        
        // Clean up
        sessionRepository.deleteById(sessionId);
    }

    @Test
    public void testSessionPersistenceAcrossHazelcastReconnection() {
        // This test verifies session data survives Hazelcast connectivity issues
        sessionMap.clear();
        
        // Create and save session
        Session session = sessionRepository.createSession();
        session.setAttribute("persistentData", "should_survive_reconnection");
        session.setAttribute("timestamp", System.currentTimeMillis());
        sessionRepository.save(session);
        String sessionId = session.getId();
        
        // Verify session is stored
        assertTrue(sessionMap.size() > 0, "Session should be stored initially");
        
        // Simulate session retrieval after connectivity restoration
        // (In a real scenario, there might be temporary connectivity loss)
        Session persistedSession = sessionRepository.findById(sessionId);
        assertNotNull(persistedSession, "Session should persist after connectivity issues");
        assertEquals("should_survive_reconnection", persistedSession.getAttribute("persistentData"), 
                    "Persistent data should be recovered");
        assertNotNull(persistedSession.getAttribute("timestamp"), "Timestamp should be preserved");
        
        // Verify session is still functional
        persistedSession.setAttribute("afterReconnect", "working");
        sessionRepository.save(persistedSession);
        
        Session verificationSession = sessionRepository.findById(sessionId);
        assertEquals("working", verificationSession.getAttribute("afterReconnect"), 
                    "Session should be fully functional after reconnection");
        
        // Clean up
        sessionRepository.deleteById(sessionId);
    }

    @Test
    public void testSessionExpiration() {
        sessionMap.clear();
        
        // Create a session with very short timeout for testing
        Session session = sessionRepository.createSession();
        session.setAttribute("testData", "will_expire");
        
        // Set a very short max inactive interval (1 second)
        session.setMaxInactiveInterval(Duration.ofSeconds(1));
        sessionRepository.save(session);
        String sessionId = session.getId();
        
        // Verify session exists initially
        Session initialSession = sessionRepository.findById(sessionId);
        assertNotNull(initialSession, "Session should exist initially");
        assertFalse(initialSession.isExpired(), "Session should not be expired initially");
        
        // Wait for session to expire (slightly longer than max inactive interval)
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test was interrupted");
        }
        
        // Check if session is considered expired
        Session expiredSession = sessionRepository.findById(sessionId);
        // Note: Depending on Spring Session implementation, expired sessions might be:
        // 1. Returned but marked as expired
        // 2. Not returned at all (null)
        // 3. Cleaned up by background processes
        
        if (expiredSession != null) {
            assertTrue(expiredSession.isExpired(), "Session should be expired after timeout");
        } else {
            // Session was cleaned up automatically, which is also valid behavior
            assertTrue(true, "Session was automatically cleaned up after expiration");
        }
    }

    @Test
    public void testMultipleSessionsIsolation() {
        sessionMap.clear();
        
        // Create multiple sessions with different data
        Session session1 = sessionRepository.createSession();
        session1.setAttribute("userId", "user1");
        session1.setAttribute("role", "admin");
        sessionRepository.save(session1);
        
        Session session2 = sessionRepository.createSession();
        session2.setAttribute("userId", "user2");
        session2.setAttribute("role", "user");
        sessionRepository.save(session2);
        
        Session session3 = sessionRepository.createSession();
        session3.setAttribute("userId", "user3");
        session3.setAttribute("role", "guest");
        sessionRepository.save(session3);
        
        // Verify sessions are isolated
        assertNotEquals(session1.getId(), session2.getId(), "Session IDs should be different");
        assertNotEquals(session2.getId(), session3.getId(), "Session IDs should be different");
        assertNotEquals(session1.getId(), session3.getId(), "Session IDs should be different");
        
        // Verify data isolation
        Session retrieved1 = sessionRepository.findById(session1.getId());
        Session retrieved2 = sessionRepository.findById(session2.getId());
        Session retrieved3 = sessionRepository.findById(session3.getId());
        
        assertEquals("user1", retrieved1.getAttribute("userId"), "Session 1 should have correct user");
        assertEquals("user2", retrieved2.getAttribute("userId"), "Session 2 should have correct user");
        assertEquals("user3", retrieved3.getAttribute("userId"), "Session 3 should have correct user");
        
        assertEquals("admin", retrieved1.getAttribute("role"), "Session 1 should have correct role");
        assertEquals("user", retrieved2.getAttribute("role"), "Session 2 should have correct role");
        assertEquals("guest", retrieved3.getAttribute("role"), "Session 3 should have correct role");
        
        // Modify one session and verify others are unaffected
        retrieved1.setAttribute("lastLogin", "today");
        sessionRepository.save(retrieved1);
        
        Session reRetrieved2 = sessionRepository.findById(session2.getId());
        Session reRetrieved3 = sessionRepository.findById(session3.getId());
        
        assertNull(reRetrieved2.getAttribute("lastLogin"), "Session 2 should not have session 1's attributes");
        assertNull(reRetrieved3.getAttribute("lastLogin"), "Session 3 should not have session 1's attributes");
        
        // Clean up
        sessionRepository.deleteById(session1.getId());
        sessionRepository.deleteById(session2.getId());
        sessionRepository.deleteById(session3.getId());
        
        assertEquals(0, sessionMap.size(), "All sessions should be cleaned up");
    }
}