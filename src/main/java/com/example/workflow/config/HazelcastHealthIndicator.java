package com.example.workflow.config;

import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "management.health.hazelcast", name = "enabled", havingValue = "true", matchIfMissing = false)
public class HazelcastHealthIndicator implements HealthIndicator {
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    @Override
    public Health health() {
        try {
            if (hazelcastInstance == null) {
                return Health.down()
                    .withDetail("status", "Hazelcast instance is null")
                    .build();
            }
            
            // Check if Hazelcast instance is running by trying to access cluster info
            boolean isRunning = hazelcastInstance.getLifecycleService().isRunning();
            
            if (isRunning) {
                return Health.up()
                    .withDetail("instanceName", hazelcastInstance.getName())
                    .withDetail("running", true)
                    .withDetail("clusterSize", hazelcastInstance.getCluster().getMembers().size())
                    .withDetail("localMember", hazelcastInstance.getCluster().getLocalMember().toString())
                    .build();
            } else {
                return Health.down()
                    .withDetail("instanceName", hazelcastInstance.getName())
                    .withDetail("running", false)
                    .build();
            }
            
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }
}