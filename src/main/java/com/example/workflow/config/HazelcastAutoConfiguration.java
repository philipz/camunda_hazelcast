package com.example.workflow.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.example.workflow.transaction.HazelcastTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "hazelcast", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HazelcastAutoConfiguration {
    
    @Autowired
    private HazelcastProperties hazelcastProperties;
    
    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setInstanceName(hazelcastProperties.getInstanceName());
        
        // Configure the map for workflow data storage
        configureWorkflowMap(config);
        
        // Configure the map for session storage
        configureSessionMap(config);
        
        // Configure transaction-specific maps
        configureTransactionMaps(config);
        
        return config;
    }
    
    private void configureWorkflowMap(Config config) {
        MapConfig workflowMapConfig = new MapConfig();
        workflowMapConfig.setName(hazelcastProperties.getMap().getName());
        workflowMapConfig.setBackupCount(hazelcastProperties.getMap().getBackupCount());
        workflowMapConfig.setTimeToLiveSeconds(hazelcastProperties.getMap().getTimeToLiveSeconds());
        config.addMapConfig(workflowMapConfig);
    }
    
    private void configureSessionMap(Config config) {
        MapConfig sessionMapConfig = new MapConfig();
        sessionMapConfig.setName(hazelcastProperties.getSession().getMapName());
        sessionMapConfig.setBackupCount(1); // At least one backup for session reliability
        sessionMapConfig.setTimeToLiveSeconds(
            hazelcastProperties.getSession().getMaxInactiveIntervalMinutes() * 60 + 300 // Add 5 minutes buffer
        );
        sessionMapConfig.setMaxIdleSeconds(
            hazelcastProperties.getSession().getMaxInactiveIntervalMinutes() * 60
        );
        config.addMapConfig(sessionMapConfig);
    }
    
    @Bean
    public HazelcastInstance hazelcastInstance(Config hazelcastConfig) {
        return Hazelcast.newHazelcastInstance(hazelcastConfig);
    }
    
    private void configureTransactionMaps(Config config) {
        // Configure active transactions map
        MapConfig activeTransactionsMapConfig = new MapConfig();
        activeTransactionsMapConfig.setName("active-transactions");
        activeTransactionsMapConfig.setBackupCount(1); // Ensure transaction metadata is backed up
        activeTransactionsMapConfig.setTimeToLiveSeconds((int) hazelcastProperties.getTransaction().getTimeoutSeconds() * 2); // Cleanup stale transactions
        config.addMapConfig(activeTransactionsMapConfig);
        
        // Configure transaction data map with stronger consistency
        MapConfig transactionDataMapConfig = new MapConfig();
        transactionDataMapConfig.setName("transaction-data");
        transactionDataMapConfig.setBackupCount(hazelcastProperties.getMap().getBackupCount());
        transactionDataMapConfig.setTimeToLiveSeconds(0); // No expiration for transaction data
        config.addMapConfig(transactionDataMapConfig);
    }
    
    @Bean
    @ConditionalOnProperty(prefix = "hazelcast.transaction", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HazelcastTransactionManager hazelcastTransactionManager(HazelcastInstance hazelcastInstance) {
        return new HazelcastTransactionManager(hazelcastInstance);
    }
}