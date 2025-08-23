package com.example.workflow;

import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

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
    } catch (Exception e) {
      logger.error("❌ Error checking Hazelcast status: {}", e.getMessage());
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
    
    logger.info("=== Integration check complete ===");
  }
}