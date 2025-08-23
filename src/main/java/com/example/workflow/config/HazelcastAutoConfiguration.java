package com.example.workflow.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
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
        MapConfig mapConfig = new MapConfig();
        mapConfig.setName(hazelcastProperties.getMap().getName());
        mapConfig.setBackupCount(hazelcastProperties.getMap().getBackupCount());
        mapConfig.setTimeToLiveSeconds(hazelcastProperties.getMap().getTimeToLiveSeconds());
        
        config.addMapConfig(mapConfig);
        
        // Disable multicast for embedded usage
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        
        return config;
    }
    
    @Bean
    public HazelcastInstance hazelcastInstance(Config hazelcastConfig) {
        return Hazelcast.newHazelcastInstance(hazelcastConfig);
    }
}