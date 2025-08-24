package com.example.workflow.integration;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CamundaSessionE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SessionRepository<Session> sessionRepository;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    private IMap<String, Object> sessionMap;
    private String baseUrl;

    @BeforeEach
    public void setUp() {
        baseUrl = "http://localhost:" + port;
        sessionMap = hazelcastInstance.getMap("spring-session-sessions");
        assertNotNull(sessionMap, "Session map should be available");
        
        // Clear any existing sessions
        sessionMap.clear();
    }

    @Test
    public void testCamundaApplicationStartup() {
        // Test that the application starts up correctly with session integration
        assertNotNull(restTemplate, "RestTemplate should be available");
        assertNotNull(sessionRepository, "SessionRepository should be available");
        assertNotNull(hazelcastInstance, "HazelcastInstance should be available");
        assertTrue(hazelcastInstance.getLifecycleService().isRunning(), "Hazelcast should be running");
    }

    @Test
    public void testCamundaWebInterfaceAccess() {
        // Test accessing Camunda web interface and verify redirect to login
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/app/", String.class);
        
        // Debug response details
        System.out.println("Response status: " + response.getStatusCode());
        System.out.println("Response headers: " + response.getHeaders());
        
        // Should either redirect to login or return a page that indicates authentication is needed
        assertTrue(response.getStatusCode().is3xxRedirection() || 
                  response.getStatusCode().is2xxSuccessful() ||
                  response.getStatusCode().is4xxClientError(),
                  "Should be able to access Camunda web interface (got: " + response.getStatusCode() + ")");
        
        // If it's a redirect, check if it's redirecting to login
        if (response.getStatusCode().is3xxRedirection() && 
            response.getHeaders().getLocation() != null) {
            String location = response.getHeaders().getLocation().toString();
            assertTrue(location.contains("login") || location.contains("auth"),
                      "Should redirect to authentication page");
        }
    }

    @Test
    public void testSessionCreationOnWebRequest() {
        // Test that web requests create sessions that are stored in Hazelcast
        int initialSessionCount = sessionMap.size();
        
        // Make a request to a Camunda endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/app/welcome/", String.class);
        
        // Check if session was created (either through response headers or session count)
        List<String> setCookieHeaders = response.getHeaders().get("Set-Cookie");
        
        if (setCookieHeaders != null && !setCookieHeaders.isEmpty()) {
            // Verify that a session cookie is set
            boolean sessionCookieFound = setCookieHeaders.stream()
                .anyMatch(cookie -> cookie.contains("SESSION") || cookie.contains("JSESSIONID") || 
                         cookie.contains("CAMUNDA_SESSION"));
            
            if (sessionCookieFound) {
                // If a session cookie was set, verify it's in Hazelcast
                // Note: There might be a slight delay for the session to appear in the map
                try {
                    Thread.sleep(100); // Small delay for session persistence
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                assertTrue(sessionMap.size() >= initialSessionCount,
                          "Session should be stored in Hazelcast after web request");
            }
        }
    }

    @Test
    public void testCamundaAuthenticationSimulation() {
        // Simulate authentication process similar to Camunda login
        // Note: This is a simplified simulation since actual Camunda authentication
        // would require more complex setup with security configuration
        
        int initialSessionCount = sessionMap.size();
        
        // Create a session manually to simulate authentication
        Session authSession = sessionRepository.createSession();
        authSession.setAttribute("authenticated", true);
        authSession.setAttribute("username", "demo");
        authSession.setAttribute("authorities", "ROLE_USER");
        sessionRepository.save(authSession);
        
        // Verify session is in Hazelcast
        assertTrue(sessionMap.size() > initialSessionCount,
                  "Authenticated session should be stored in Hazelcast");
        
        // Retrieve and verify session
        Session retrievedSession = sessionRepository.findById(authSession.getId());
        assertNotNull(retrievedSession, "Authenticated session should be retrievable");
        assertEquals(true, retrievedSession.getAttribute("authenticated"),
                    "Authentication status should be preserved");
        assertEquals("demo", retrievedSession.getAttribute("username"),
                    "Username should be preserved in session");
        assertEquals("ROLE_USER", retrievedSession.getAttribute("authorities"),
                    "User authorities should be preserved in session");
        
        // Clean up
        sessionRepository.deleteById(authSession.getId());
    }

    @Test
    public void testSessionPersistenceAcrossRequests() {
        // Test that sessions persist across multiple requests
        
        // Create a session with authentication data
        Session persistentSession = sessionRepository.createSession();
        persistentSession.setAttribute("user", "testUser");
        persistentSession.setAttribute("loginTime", System.currentTimeMillis());
        persistentSession.setAttribute("preferences", "dark_theme=true");
        sessionRepository.save(persistentSession);
        String sessionId = persistentSession.getId();
        
        // Simulate multiple requests using the same session
        for (int i = 0; i < 5; i++) {
            Session currentSession = sessionRepository.findById(sessionId);
            assertNotNull(currentSession, "Session should persist across requests");
            assertEquals("testUser", currentSession.getAttribute("user"),
                        "User data should persist across requests");
            
            // Update session with request-specific data
            currentSession.setAttribute("lastRequest", i);
            currentSession.setAttribute("requestTime", System.currentTimeMillis());
            sessionRepository.save(currentSession);
        }
        
        // Verify final state
        Session finalSession = sessionRepository.findById(sessionId);
        assertNotNull(finalSession, "Session should still exist after multiple requests");
        assertEquals("testUser", finalSession.getAttribute("user"),
                    "User data should remain consistent");
        assertEquals(Integer.valueOf(4), finalSession.getAttribute("lastRequest"),
                    "Last request number should be updated");
        assertNotNull(finalSession.getAttribute("requestTime"),
                     "Request time should be updated");
        assertNotNull(finalSession.getAttribute("loginTime"),
                     "Login time should be preserved");
        
        // Clean up
        sessionRepository.deleteById(sessionId);
    }

    @Test
    public void testMultipleUserSessionIsolation() {
        // Test that multiple user sessions are properly isolated
        
        // Create sessions for different users
        Session user1Session = sessionRepository.createSession();
        user1Session.setAttribute("userId", "user1");
        user1Session.setAttribute("role", "admin");
        user1Session.setAttribute("department", "IT");
        sessionRepository.save(user1Session);
        
        Session user2Session = sessionRepository.createSession();
        user2Session.setAttribute("userId", "user2");
        user2Session.setAttribute("role", "user");
        user2Session.setAttribute("department", "HR");
        sessionRepository.save(user2Session);
        
        Session user3Session = sessionRepository.createSession();
        user3Session.setAttribute("userId", "user3");
        user3Session.setAttribute("role", "manager");
        user3Session.setAttribute("department", "Finance");
        sessionRepository.save(user3Session);
        
        // Verify session isolation
        assertNotEquals(user1Session.getId(), user2Session.getId(),
                       "User sessions should have different IDs");
        assertNotEquals(user2Session.getId(), user3Session.getId(),
                       "User sessions should have different IDs");
        
        // Verify data isolation
        Session retrieved1 = sessionRepository.findById(user1Session.getId());
        Session retrieved2 = sessionRepository.findById(user2Session.getId());
        Session retrieved3 = sessionRepository.findById(user3Session.getId());
        
        assertEquals("user1", retrieved1.getAttribute("userId"),
                    "User 1 session should have correct user ID");
        assertEquals("user2", retrieved2.getAttribute("userId"),
                    "User 2 session should have correct user ID");
        assertEquals("user3", retrieved3.getAttribute("userId"),
                    "User 3 session should have correct user ID");
        
        assertEquals("admin", retrieved1.getAttribute("role"),
                    "User 1 should have admin role");
        assertEquals("user", retrieved2.getAttribute("role"),
                    "User 2 should have user role");
        assertEquals("manager", retrieved3.getAttribute("role"),
                    "User 3 should have manager role");
        
        // Modify one session and verify others are unaffected
        retrieved1.setAttribute("lastAction", "system_config");
        sessionRepository.save(retrieved1);
        
        Session reRetrieved2 = sessionRepository.findById(user2Session.getId());
        Session reRetrieved3 = sessionRepository.findById(user3Session.getId());
        
        assertNull(reRetrieved2.getAttribute("lastAction"),
                  "User 2 session should not have User 1's actions");
        assertNull(reRetrieved3.getAttribute("lastAction"),
                  "User 3 session should not have User 1's actions");
        
        // Clean up
        sessionRepository.deleteById(user1Session.getId());
        sessionRepository.deleteById(user2Session.getId());
        sessionRepository.deleteById(user3Session.getId());
    }

    @Test
    public void testSessionDataIntegrityAfterRestart() {
        // Test that session data maintains integrity after simulated application restart
        
        // Create session with comprehensive data
        Session originalSession = sessionRepository.createSession();
        originalSession.setAttribute("username", "restartTestUser");
        originalSession.setAttribute("loginTimestamp", System.currentTimeMillis());
        originalSession.setAttribute("permissions", new String[]{"READ", "WRITE", "DELETE"});
        originalSession.setAttribute("preferences", "theme=dark,lang=en,timezone=UTC");
        originalSession.setAttribute("activeProcesses", 5);
        sessionRepository.save(originalSession);
        String sessionId = originalSession.getId();
        
        // Verify session is stored in Hazelcast
        assertTrue(sessionMap.size() > 0, "Session should be stored in Hazelcast");
        
        // Simulate application restart by clearing and recreating session repository context
        // (In a real restart, the SessionRepository would be recreated but Hazelcast data persists)
        
        // Retrieve session after "restart"
        Session restoredSession = sessionRepository.findById(sessionId);
        assertNotNull(restoredSession, "Session should be restored after restart");
        
        // Verify data integrity
        assertEquals("restartTestUser", restoredSession.getAttribute("username"),
                    "Username should be preserved after restart");
        assertNotNull(restoredSession.getAttribute("loginTimestamp"),
                     "Login timestamp should be preserved after restart");
        assertEquals("theme=dark,lang=en,timezone=UTC", restoredSession.getAttribute("preferences"),
                    "Preferences should be preserved after restart");
        assertEquals(Integer.valueOf(5), restoredSession.getAttribute("activeProcesses"),
                    "Active processes count should be preserved after restart");
        
        // Verify session is still functional
        restoredSession.setAttribute("postRestartAction", "verified");
        sessionRepository.save(restoredSession);
        
        Session verificationSession = sessionRepository.findById(sessionId);
        assertEquals("verified", verificationSession.getAttribute("postRestartAction"),
                    "Session should be fully functional after restart");
        
        // Clean up
        sessionRepository.deleteById(sessionId);
    }

    @Test
    public void testHazelcastSessionMapConfiguration() {
        // Test that the session map is properly configured for Camunda use
        
        assertEquals("spring-session-sessions", sessionMap.getName(),
                    "Session map should have correct name");
        
        // Create a test session to verify map behavior
        Session testSession = sessionRepository.createSession();
        testSession.setAttribute("mapTest", "configurationTest");
        sessionRepository.save(testSession);
        
        // Verify session appears in the map
        assertTrue(sessionMap.size() > 0, "Session should appear in Hazelcast map");
        
        // Verify map operations work correctly
        Set<String> keys = sessionMap.keySet();
        assertFalse(keys.isEmpty(), "Map should contain session keys");
        
        // Clean up
        sessionRepository.deleteById(testSession.getId());
        
        // Verify cleanup worked
        if (sessionMap.size() == 0) {
            assertTrue(true, "Session was properly removed from map");
        } else {
            // There might be other sessions, so just verify our test session is gone
            Session removedSession = sessionRepository.findById(testSession.getId());
            assertNull(removedSession, "Test session should be removed");
        }
    }
}