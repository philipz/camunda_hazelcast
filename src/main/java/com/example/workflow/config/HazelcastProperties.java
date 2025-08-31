package com.example.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hazelcast")
public class HazelcastProperties {
    
    private String instanceName = "camunda-hazelcast";
    private boolean enabled = true;
    private Map map = new Map();
    private Session session = new Session();
    private Transaction transaction = new Transaction();
    
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
    
    public Session getSession() {
        return session;
    }
    
    public void setSession(Session session) {
        this.session = session;
    }
    
    public Transaction getTransaction() {
        return transaction;
    }
    
    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }
    
    public static class Map {
        private String name = "myMap";
        private int backupCount = 1;
        private int timeToLiveSeconds = 0; // 0 = no expiration
        
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
    
    public static class Session {
        private String mapName = "spring-session-sessions";
        private int maxInactiveIntervalMinutes = 30;
        private String cookieName = "CAMUNDA_SESSION";
        private boolean cookieSecure = true;
        private boolean cookieHttpOnly = true;
        
        public String getMapName() {
            return mapName;
        }
        
        public void setMapName(String mapName) {
            this.mapName = mapName;
        }
        
        public int getMaxInactiveIntervalMinutes() {
            return maxInactiveIntervalMinutes;
        }
        
        public void setMaxInactiveIntervalMinutes(int maxInactiveIntervalMinutes) {
            this.maxInactiveIntervalMinutes = maxInactiveIntervalMinutes;
        }
        
        public String getCookieName() {
            return cookieName;
        }
        
        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }
        
        public boolean isCookieSecure() {
            return cookieSecure;
        }
        
        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
        
        public boolean isCookieHttpOnly() {
            return cookieHttpOnly;
        }
        
        public void setCookieHttpOnly(boolean cookieHttpOnly) {
            this.cookieHttpOnly = cookieHttpOnly;
        }
    }
    
    public static class Transaction {
        private long timeoutSeconds = 30L;
        private TransactionType type = TransactionType.TWO_PHASE;
        private String isolation = "READ_COMMITTED";
        private int retryCount = 3;
        private boolean enableXA = false;
        
        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }
        
        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
        
        public TransactionType getType() {
            return type;
        }
        
        public void setType(TransactionType type) {
            this.type = type;
        }
        
        public String getIsolation() {
            return isolation;
        }
        
        public void setIsolation(String isolation) {
            this.isolation = isolation;
        }
        
        public int getRetryCount() {
            return retryCount;
        }
        
        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }
        
        public boolean isEnableXA() {
            return enableXA;
        }
        
        public void setEnableXA(boolean enableXA) {
            this.enableXA = enableXA;
        }
    }
    
    public enum TransactionType {
        TWO_PHASE,
        ONE_PHASE
    }
}