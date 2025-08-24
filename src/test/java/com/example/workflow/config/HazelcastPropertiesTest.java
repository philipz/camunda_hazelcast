package com.example.workflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class HazelcastPropertiesTest {

    @Autowired
    private HazelcastProperties hazelcastProperties;

    @Test
    public void testDefaultSessionProperties() {
        assertNotNull(hazelcastProperties, "HazelcastProperties should be injected");
        assertNotNull(hazelcastProperties.getSession(), "Session properties should be available");
        
        HazelcastProperties.Session sessionProps = hazelcastProperties.getSession();
        
        // Test default values
        assertEquals("spring-session-sessions", sessionProps.getMapName(), 
                    "Default session map name should be 'spring-session-sessions'");
        assertEquals(30, sessionProps.getMaxInactiveIntervalMinutes(), 
                    "Default session timeout should be 30 minutes");
        assertEquals("CAMUNDA_SESSION", sessionProps.getCookieName(), 
                    "Default cookie name should be 'CAMUNDA_SESSION'");
        assertTrue(sessionProps.isCookieSecure(), 
                  "Cookie secure should be true by default");
        assertTrue(sessionProps.isCookieHttpOnly(), 
                  "Cookie HTTP-only should be true by default");
    }

    @Test
    public void testSessionPropertySetters() {
        HazelcastProperties.Session sessionProps = new HazelcastProperties.Session();
        
        // Test all setters and getters
        sessionProps.setMapName("custom-sessions");
        assertEquals("custom-sessions", sessionProps.getMapName(), 
                    "Map name should be settable");
        
        sessionProps.setMaxInactiveIntervalMinutes(60);
        assertEquals(60, sessionProps.getMaxInactiveIntervalMinutes(), 
                    "Max inactive interval should be settable");
        
        sessionProps.setCookieName("CUSTOM_SESSION");
        assertEquals("CUSTOM_SESSION", sessionProps.getCookieName(), 
                    "Cookie name should be settable");
        
        sessionProps.setCookieSecure(false);
        assertFalse(sessionProps.isCookieSecure(), 
                   "Cookie secure should be settable");
        
        sessionProps.setCookieHttpOnly(false);
        assertFalse(sessionProps.isCookieHttpOnly(), 
                   "Cookie HTTP-only should be settable");
    }

    @Test
    public void testHazelcastPropertiesIntegration() {
        // Test that session properties are properly integrated with main properties
        assertNotNull(hazelcastProperties.getSession(), "Session should be accessible");
        assertTrue(hazelcastProperties.isEnabled(), "Hazelcast should be enabled");
        assertTrue(hazelcastProperties.getInstanceName().startsWith("camunda-hazelcast"), 
                    "Instance name should start with camunda-hazelcast prefix");
        
        // Test that we can access session properties through main properties
        HazelcastProperties.Session session = hazelcastProperties.getSession();
        assertNotNull(session, "Session properties should be accessible");
    }

    @Test
    public void testSessionPropertyBinding() {
        // Test the property binding mechanism with default values
        HazelcastProperties.Session sessionProps = hazelcastProperties.getSession();
        assertNotNull(sessionProps, "Session properties should be bound");
        
        // Verify that properties are properly bound with default values
        assertNotNull(sessionProps.getMapName(), "Map name should be bound");
        assertTrue(sessionProps.getMaxInactiveIntervalMinutes() > 0, 
                  "Max inactive interval should be positive");
        assertNotNull(sessionProps.getCookieName(), "Cookie name should be bound");
        
        // Test that properties can be modified (proving they are mutable beans)
        String originalMapName = sessionProps.getMapName();
        sessionProps.setMapName("test-binding");
        assertEquals("test-binding", sessionProps.getMapName(), "Property should be modifiable");
        
        // Restore original value
        sessionProps.setMapName(originalMapName);
    }

    @Test
    public void testSessionPropertiesValidation() {
        HazelcastProperties.Session sessionProps = new HazelcastProperties.Session();
        
        // Test reasonable values
        sessionProps.setMaxInactiveIntervalMinutes(1); // 1 minute minimum
        assertTrue(sessionProps.getMaxInactiveIntervalMinutes() > 0, 
                  "Session timeout should be positive");
        
        sessionProps.setMaxInactiveIntervalMinutes(1440); // 24 hours
        assertTrue(sessionProps.getMaxInactiveIntervalMinutes() <= 1440, 
                  "Session timeout should be reasonable");
        
        // Test empty/null values handling
        sessionProps.setMapName("");
        assertNotNull(sessionProps.getMapName(), "Map name should not be null");
        
        sessionProps.setCookieName("");
        assertNotNull(sessionProps.getCookieName(), "Cookie name should not be null");
    }

    @Test 
    public void testSessionPropertiesReference() {
        // Test that multiple calls return the same object reference
        HazelcastProperties.Session session1 = hazelcastProperties.getSession();
        HazelcastProperties.Session session2 = hazelcastProperties.getSession();
        
        // Verify that both references point to the same object (expected behavior)
        assertSame(session1, session2, "Session properties should return the same object instance");
        
        // Test that modifications are reflected in both references
        String originalMapName = session1.getMapName();
        session1.setMapName("modified-map");
        assertEquals("modified-map", session2.getMapName(), 
                    "Changes should be reflected in all references");
        
        // Restore original value
        session1.setMapName(originalMapName);
        assertEquals(originalMapName, session2.getMapName(), 
                    "Restoration should be reflected in all references");
    }

    @Test
    public void testNestedPropertyStructure() {
        // Test that the nested property structure works correctly
        assertNotNull(hazelcastProperties.getMap(), "Map properties should be available");
        assertNotNull(hazelcastProperties.getSession(), "Session properties should be available");
        
        // Test that session and map properties are independent
        HazelcastProperties.Map mapProps = hazelcastProperties.getMap();
        HazelcastProperties.Session sessionProps = hazelcastProperties.getSession();
        
        assertNotEquals(mapProps.getName(), sessionProps.getMapName(), 
                       "Map name and session map name should be different");
        
        // Verify default values are different
        assertEquals("myMap", mapProps.getName(), "Regular map should have default name");
        assertEquals("spring-session-sessions", sessionProps.getMapName(), 
                    "Session map should have session-specific name");
    }
}