package com.example.workflow;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.session.SessionRepository;
import com.hazelcast.core.HazelcastException;

@SpringBootApplication
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);
  
  @Autowired
  private ApplicationContext applicationContext;

  public static void main(String... args) {
    SpringApplication.run(Application.class, args);
  }

  @EventListener
  public void onApplicationReady(ApplicationReadyEvent event) {
    logger.info("=== Camunda Hazelcast Integration Status ===");
    
    try {
      // Verify Hazelcast instance
      HazelcastInstance hazelcastInstance = applicationContext.getBean(HazelcastInstance.class);
      if (hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning()) {
        logger.info("✅ Hazelcast instance '{}' is running", hazelcastInstance.getName());
        logger.info("✅ Hazelcast cluster size: {}", hazelcastInstance.getCluster().getMembers().size());
      } else {
        logger.warn("⚠️ Hazelcast instance is not running properly");
      }
    } catch (HazelcastException e) {
      logger.error("❌ Hazelcast error: {}", e.getMessage());
    } catch (Exception e) {
      logger.error("❌ Unexpected error checking Hazelcast status: {}", e.getMessage(), e);
    }
    
    try {
      // Verify service delegates are registered
      boolean putDelegateExists = applicationContext.containsBean("putServiceDelegate");
      boolean getDelegateExists = applicationContext.containsBean("getServiceDelegate");
      
      logger.info("✅ Service Delegates Registration:");
      logger.info("  - putServiceDelegate: {}", putDelegateExists ? "✅ registered" : "❌ missing");
      logger.info("  - getServiceDelegate: {}", getDelegateExists ? "✅ registered" : "❌ missing");
      
    } catch (Exception e) {
      logger.error("❌ Error checking service delegates: {}", e.getMessage());
    }
    
    try {
      // Verify Spring Session integration
      SessionRepository<?> sessionRepository = applicationContext.getBean(SessionRepository.class);
      if (sessionRepository != null) {
        logger.info("✅ Spring Session repository is registered: {}", sessionRepository.getClass().getSimpleName());
        
        // Verify session map is configured in Hazelcast
        HazelcastInstance hazelcastInstance = applicationContext.getBean(HazelcastInstance.class);
        if (hazelcastInstance != null) {
          IMap<String, Object> sessionMap = hazelcastInstance.getMap("spring-session-sessions");
          if (sessionMap != null) {
            logger.info("✅ Spring Session Hazelcast map 'spring-session-sessions' is configured");
            logger.info("  - Current sessions in map: {}", sessionMap.size());
          } else {
            logger.warn("⚠️ Spring Session map 'spring-session-sessions' not found in Hazelcast");
          }
        }
      } else {
        logger.warn("⚠️ Spring Session repository is not registered");
      }
    } catch (Exception e) {
      logger.error("❌ Error checking Spring Session status: {}", e.getMessage());
    }
    
    logger.info("=== Integration check complete ===");
  }
}