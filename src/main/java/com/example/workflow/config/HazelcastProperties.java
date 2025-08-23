package com.example.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hazelcast")
public class HazelcastProperties {
    
    private String instanceName = "camunda-hazelcast";
    private boolean enabled = true;
    private Map map = new Map();
    
    public String getInstanceName() {
        return instanceName;
    }
    
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Map getMap() {
        return map;
    }
    
    public void setMap(Map map) {
        this.map = map;
    }
    
    public static class Map {
        private String name = "myMap";
        private int backupCount = 1;
        private int timeToLiveSeconds = 3600;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public int getBackupCount() {
            return backupCount;
        }
        
        public void setBackupCount(int backupCount) {
            this.backupCount = backupCount;
        }
        
        public int getTimeToLiveSeconds() {
            return timeToLiveSeconds;
        }
        
        public void setTimeToLiveSeconds(int timeToLiveSeconds) {
            this.timeToLiveSeconds = timeToLiveSeconds;
        }
    }
}