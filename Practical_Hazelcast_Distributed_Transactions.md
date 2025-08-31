# Practical Hazelcast Distributed Transactions: Complete Implementation Guide

Hazelcast distributed transactions provide powerful capabilities for coordinating operations across multiple services and data sources in microservices architectures. This comprehensive guide combines real-world code examples, production-ready GitHub repositories, and current best practices to help you implement robust distributed transaction solutions with Hazelcast Platform 5.x.

## Understanding Hazelcast distributed transaction fundamentals

**Hazelcast Platform 5.x offers two primary transaction modes** that serve different use cases. TWO_PHASE transactions provide stronger consistency guarantees by implementing a prepare-then-commit protocol with distributed commit logs, making them ideal for financial services and mission-critical applications. ONE_PHASE transactions optimize for performance with a single commit phase, suitable for scenarios where occasional consistency issues during member failures are acceptable.

The platform's **CP Subsystem delivers strong consistency** using consensus algorithms, while the **Thread-Per-Core (TPC) architecture in version 5.5** eliminates resource contention by assigning separate threads to each CPU core. These architectural improvements have enabled production deployments processing over 100,000 transactions per second with sub-20ms latencies in global payment systems.

Modern implementations leverage **XA transaction support** for coordinating with external resources like databases and message queues, ensuring ACID properties across distributed systems. The platform maintains compatibility with Java Transaction API standards while providing Hazelcast-specific optimizations for in-memory data structures.

## Complete working code examples and patterns

### Core transaction implementation patterns

The foundation of Hazelcast distributed transactions centers on the **TransactionContext interface**, which provides access to transactional data structures. Here's a complete working example demonstrating proper transaction handling:

```java
public class TransactionalOrderProcessor {
    private final HazelcastInstance hazelcastInstance;
    
    public void processOrderTransaction(Order order) throws Exception {
        TransactionOptions options = new TransactionOptions()
            .setTransactionType(TransactionOptions.TransactionType.TWO_PHASE)
            .setTimeout(30, TimeUnit.SECONDS);
            
        TransactionContext context = hazelcastInstance.newTransactionContext(options);
        
        boolean transactionStarted = false;
        try {
            context.beginTransaction();
            transactionStarted = true;
            
            // Get transactional data structures
            TransactionalMap<String, Order> orderMap = context.getMap("orders");
            TransactionalMap<String, Inventory> inventoryMap = context.getMap("inventory");
            TransactionalQueue<String> eventQueue = context.getQueue("order-events");
            
            // Perform coordinated operations
            validateAndReserveInventory(inventoryMap, order);
            processPayment(order);
            updateOrderStatus(orderMap, order);
            publishOrderEvent(eventQueue, order);
            
            // All operations succeed or fail together
            context.commitTransaction();
            
        } catch (TransactionException e) {
            if (transactionStarted) {
                context.rollbackTransaction();
            }
            throw new BusinessTransactionException("Order processing failed", e);
        }
    }
    
    private void validateAndReserveInventory(
            TransactionalMap<String, Inventory> inventoryMap, Order order) {
        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryMap.getForUpdate(item.getProductId());
            if (inventory == null || inventory.getQuantity() < item.getQuantity()) {
                throw new InsufficientInventoryException(
                    "Not enough inventory for product: " + item.getProductId());
            }
            inventory.reserve(item.getQuantity());
            inventoryMap.put(item.getProductId(), inventory);
        }
    }
}
```

### XA transaction coordination across multiple resources

**Multi-resource distributed transactions** require XA protocol implementation to coordinate between Hazelcast and external systems. This example demonstrates database and cache coordination:

```java
public class XATransactionCoordinator {
    
    public void performDistributedTransaction(String orderId, OrderData data) throws Exception {
        // Initialize resources
        HazelcastInstance instance = Hazelcast.newHazelcastInstance();
        HazelcastXAResource hazelcastXA = instance.getXAResource();
        
        XADataSource dbXADataSource = createDatabaseXADataSource();
        XAConnection xaConnection = dbXADataSource.getXAConnection();
        XAResource dbXAResource = xaConnection.getXAResource();
        
        UserTransactionManager tm = new UserTransactionManager();
        tm.setTransactionTimeout(60);
        
        try {
            tm.begin();
            Transaction transaction = tm.getTransaction();
            
            // Enlist both resources in global transaction
            transaction.enlistResource(hazelcastXA);
            transaction.enlistResource(dbXAResource);
            
            // Hazelcast operations
            TransactionContext hzContext = hazelcastXA.getTransactionContext();
            TransactionalMap<String, OrderData> orderMap = hzContext.getMap("orders");
            orderMap.put(orderId, data);
            
            // Database operations
            Connection dbConnection = xaConnection.getConnection();
            PreparedStatement stmt = dbConnection.prepareStatement(
                "INSERT INTO order_audit (order_id, timestamp, status) VALUES (?, ?, ?)");
            stmt.setString(1, orderId);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.setString(3, "PROCESSED");
            stmt.executeUpdate();
            
            // Delist resources before commit
            transaction.delistResource(hazelcastXA, XAResource.TMSUCCESS);
            transaction.delistResource(dbXAResource, XAResource.TMSUCCESS);
            
            // Two-phase commit across all resources
            tm.commit();
            
        } catch (Exception e) {
            tm.rollback();
            throw new DistributedTransactionException("XA transaction failed", e);
        } finally {
            xaConnection.close();
        }
    }
}
```

### Microservices transaction coordination patterns

**Cross-service transaction coordination** leverages Hazelcast as a shared data layer between microservices. This pattern enables ACID properties across service boundaries:

```java
@Component
public class DistributedServiceCoordinator {
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private ShippingService shippingService;
    
    public void processDistributedOrder(Order order) throws Exception {
        TransactionOptions options = new TransactionOptions()
            .setTransactionType(TransactionOptions.TransactionType.TWO_PHASE)
            .setTimeout(120, TimeUnit.SECONDS);
            
        TransactionContext context = hazelcastInstance.newTransactionContext(options);
        
        try {
            context.beginTransaction();
            
            // Coordinate across multiple services using shared maps
            TransactionalMap<String, Order> orderMap = context.getMap("orders");
            TransactionalMap<String, PaymentStatus> paymentMap = context.getMap("payments");
            TransactionalMap<String, ShippingStatus> shippingMap = context.getMap("shipping");
            TransactionalQueue<String> eventQueue = context.getQueue("order-events");
            
            // Service 1: Process payment through shared state
            PaymentResult paymentResult = paymentService.processPayment(
                order.getPayment(), context);
            paymentMap.put(order.getId(), paymentResult.getStatus());
            
            // Service 2: Arrange shipping through shared state  
            ShippingResult shippingResult = shippingService.arrangeShipping(
                order.getShipping(), context);
            shippingMap.put(order.getId(), shippingResult.getStatus());
            
            // Update final order status
            order.setStatus("CONFIRMED");
            orderMap.put(order.getId(), order);
            eventQueue.offer("ORDER_CONFIRMED:" + order.getId());
            
            // All services must succeed for transaction to commit
            context.commitTransaction();
            
        } catch (Exception e) {
            context.rollbackTransaction();
            handleTransactionFailure(order, e);
            throw e;
        }
    }
}
```

## Production-ready GitHub repositories and examples

### Official Hazelcast implementations

The **Hazelcast Code Samples repository** ([hazelcast/hazelcast-code-samples](https://github.com/hazelcast/hazelcast-code-samples)) provides the most authoritative examples. The `/transactions` folder contains comprehensive demonstrations of TransactionalMap usage, TransactionalTask interfaces, and XA transaction samples with Atomikos integration. These examples showcase both one-phase and two-phase commit patterns with extensive documentation.

**HazelcastMQ** ([mpilone/hazelcastmq](https://github.com/mpilone/hazelcastmq)) represents a **production-quality messaging layer** built on Hazelcast with comprehensive transaction support. The project includes JMS 2.0-like API, Spring Framework integration with transaction management, and XA transaction support. The `TransactionAwareHazelcastInstanceProxyFactory` provides advanced Spring transaction synchronization, making it suitable for enterprise deployments.

### Spring Boot integration examples

The **Spring Boot Hazelcast Example** ([nicusX/springboot-hazelcast-example](https://github.com/nicusX/springboot-hazelcast-example), transactions branch) demonstrates complete Spring Boot application integration with Hazelcast transactions. It includes HazelcastTransactionManager implementation, Spring @Transactional annotation support, and transactional queue polling for message processing applications.

**Comprehensive Spring Data integration** is showcased in [piomin/sample-hazelcast-spring-datagrid](https://github.com/piomin/sample-hazelcast-spring-datagrid), which provides multiple microservices examples. The employee-service and person-service demonstrate distributed data storage patterns with Spring Boot, including Kubernetes deployment configurations and MySQL integration examples.

### Enterprise-grade implementations

The **NSA Datawave Hazelcast Service** ([NationalSecurityAgency/datawave-hazelcast-service](https://github.com/NationalSecurityAgency/datawave-hazelcast-service)) exemplifies **government production standards** for microservice architecture using Hazelcast. It includes service discovery with Consul, production-ready logging and monitoring, and demonstrates security patterns required for sensitive environments.

**Hazelcast Hibernate integration** ([hazelcast/hazelcast-hibernate](https://github.com/hazelcast/hazelcast-hibernate)) provides **distributed second-level cache** implementation with JPA transaction integration and transactional guarantees. This official repository shows L2 cache patterns with Spring Boot configuration examples suitable for enterprise applications.

## Framework integration and Spring Boot patterns

### Modern Spring Boot 3.x integration approach

**Important architectural consideration**: Spring Boot's native HazelcastTransactionManager support has limitations in current versions, requiring manual transaction management for optimal results. Here's the recommended approach for new Spring Boot 3.x projects:

```java
@Service
public class ManualTransactionService {
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    public void performTransactionalOperation(String orderId, String status) {
        TransactionOptions options = new TransactionOptions()
            .setTransactionType(TransactionOptions.TransactionType.TWO_PHASE)
            .setTimeout(30, TimeUnit.SECONDS);
        
        TransactionContext context = hazelcastInstance.newTransactionContext(options);
        
        try {
            context.beginTransaction();
            
            TransactionalMap<String, String> orderStatusMap = context.getMap("order-status");
            TransactionalMap<String, Long> orderTimestampMap = context.getMap("order-timestamps");
            
            orderStatusMap.put(orderId, status);
            orderTimestampMap.put(orderId, System.currentTimeMillis());
            
            validateOrderStatus(orderId, status);
            
            context.commitTransaction();
            
        } catch (Exception e) {
            context.rollbackTransaction();
            throw new ServiceException("Failed to update order status", e);
        }
    }
}
```

### Atomikos integration with Spring Boot 3.x

**Spring Boot 3.x compatibility** requires the dedicated Atomikos starter since built-in support was removed. Here's the complete configuration:

```xml
<dependency>
    <groupId>com.atomikos</groupId>
    <artifactId>transactions-spring-boot3-starter</artifactId>
    <version>6.0.114</version>
</dependency>
```

```java
@Configuration
@EnableTransactionManagement
public class AtomikosHazelcastConfig {

    @Bean
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        config.setClusterName("atomikos-cluster");
        return Hazelcast.newHazelcastInstance(config);
    }

    @Bean
    public JtaTransactionManager transactionManager() throws SystemException {
        UserTransactionManager utm = new UserTransactionManager();
        utm.setForceShutdown(false);
        
        UserTransactionImp utx = new UserTransactionImp();
        utx.setTransactionTimeout(300);
        
        JtaTransactionManager jtaTM = new JtaTransactionManager();
        jtaTM.setTransactionManager(utm);
        jtaTM.setUserTransaction(utx);
        return jtaTM;
    }
}
```

### Narayana transaction manager integration

**Enterprise-grade JTA implementation** with Red Hat backing provides robust distributed transaction capabilities:

```java
@Configuration
public class NarayanaHazelcastConfig {

    @Bean
    public TransactionManager narayanaTransactionManager() {
        return new TransactionManagerImple();
    }

    @Bean
    public UserTransaction narayanaUserTransaction() {
        return new UserTransactionImple();
    }

    @Bean
    public JtaTransactionManager transactionManager() {
        JtaTransactionManager tm = new JtaTransactionManager();
        tm.setTransactionManager(narayanaTransactionManager());
        tm.setUserTransaction(narayanaUserTransaction());
        return tm;
    }
}
```

## Real-world implementation patterns and industry examples

### Financial services distributed transaction architectures

**Global cross-border payment systems** represent the most demanding distributed transaction requirements. A production system processing **100,000 transactions per second with sub-20ms latency** employs this architecture pattern:

The **microservices transaction pipeline** consists of interconnected services sharing a common Hazelcast cluster data layer. The ETL transformation stage performs in-memory transaction storage, while parallel fraud detection processes thousands of rules per transaction. Account balance updates maintain transactional consistency, and message routing coordinates with clearing houses, all achieving **99.999% availability**.

**Investment bank collateral management systems** process hundreds of thousands of messages per second with exactly-once processing guarantees. The event-driven architecture uses distributed transaction coordination to ensure every collateral message is processed exactly once, critical for managing financial exposure risk.

### E-commerce transaction coordination at scale

**Multi-billion dollar e-commerce platforms** leverage Hazelcast for extreme burst traffic during product launches and shopping events. One implementation serving **$18.3 billion in annual revenue** evolved from hundreds of nodes with open-source Hazelcast to a **dedicated cluster topology using High-Density Memory Store (HDMS)**, reducing infrastructure from hundreds of servers to just 6 servers with 28GB cache space each.

**Global payment processing** achieves top-10 worldwide performance rankings through in-memory transaction processing with PCI DSS compliance. The architecture employs zero-downtime scaling with WAN replication across data centers while maintaining built-in TLS/SSL security without persistent storage of sensitive payment card data.

### Saga pattern implementation using Hazelcast

**Orchestration pattern coordination** employs a central orchestrator service managing transaction state in Hazelcast distributed maps. State storage uses distributed maps for saga execution state and compensation logs, while ITopic publishes saga events and state transitions. The IExecutorService executes compensation transactions during failures, providing real-time saga state visibility through distributed data structures.

**Framework integration examples** include Axon Saga with lightweight Spring Boot integration using Hazelcast as event store, Eclipse MicroProfile LRA for HTTP-based saga coordination with Hazelcast persistence, and Eventuate Tram Saga providing Spring Boot/Micronaut framework support with Hazelcast backing.

## Current best practices and Platform 5.x innovations

### Thread-Per-Core architecture optimization

**Hazelcast Platform 5.5 introduces revolutionary Thread-Per-Core (TPC) architecture** that eliminates resource contention by assigning separate threads to each CPU core. This cutting-edge design requires ALL_MEMBERS routing configuration and delivers predictable performance characteristics optimal for compute-intensive workloads.

**Performance optimization results** demonstrate reduced cluster sizes from hundreds to single-digit nodes using High-Density Memory Store (HDMS), predictable garbage collection behavior, and linear scaling characteristics supporting 28GB+ cache space per server.

### Advanced monitoring and observability patterns

**Production monitoring implementations** integrate Prometheus exporters with Management Center for comprehensive metric collection. Key performance indicators include transaction throughput and latency percentiles, error rates and failure patterns, resource utilization for capacity planning, and business-level SLA compliance tracking.

```java
@Component
public class TransactionMetrics {
    
    private final Counter transactionCounter;
    private final Timer transactionTimer;
    
    public TransactionMetrics(MeterRegistry meterRegistry) {
        this.transactionCounter = Counter.builder("hazelcast.transactions.total")
            .description("Total number of Hazelcast transactions")
            .register(meterRegistry);
            
        this.transactionTimer = Timer.builder("hazelcast.transactions.duration")
            .description("Hazelcast transaction duration")
            .register(meterRegistry);
    }
    
    public <T> T executeWithMetrics(Supplier<T> transactionOperation) {
        return transactionTimer.recordCallable(() -> {
            try {
                T result = transactionOperation.get();
                transactionCounter.increment(Tags.of("status", "success"));
                return result;
            } catch (Exception e) {
                transactionCounter.increment(Tags.of("status", "failure"));
                throw e;
            }
        });
    }
}
```

### Testing strategies and chaos engineering

**Comprehensive testing approaches** include XA transaction compatibility verification across PostgreSQL, MySQL, Oracle, and SQL Server databases. Spring transaction integration testing validates transactional queue implementations and exception handling with proper rollback scenarios.

**Chaos engineering integration** with chaos-mesh enables node failure simulation during transaction processing, network partition testing with split-brain scenarios, and resource constraint testing under memory pressure and CPU throttling conditions. **Real-time monitoring during chaos experiments** validates fault tolerance and data consistency post-failure.

### Security and compliance patterns

**Enterprise security frameworks** implement TLS/SSL encryption with minimal performance impact, forced client authentication before cluster access, granular authorization controls for resources and operations, and complete audit trails for transaction and access logging.

**Financial services compliance** achieves PCI DSS requirements through in-memory data processing without persistent disk storage, encrypted inter-node communication, secure client-server connections, and access control with comprehensive audit logging capabilities.

## Implementation recommendations and migration strategies

### Transaction type selection guidance

**Performance versus consistency trade-offs** require careful consideration of business requirements. Choose ONE_PHASE transactions for maximum performance when occasional consistency issues during member failures are acceptable. Select TWO_PHASE transactions for mission-critical applications requiring strict consistency guarantees, understanding the performance implications.

### Architecture design principles

**Scalability planning** requires designing for horizontal scaling from initial implementation, implementing proper partition key strategies for optimal distribution, planning for geographically distributed deployments, and carefully considering eventual consistency implications across services.

**Resilience patterns** should implement circuit breaker patterns for external dependencies, design for graceful degradation under load conditions, plan comprehensive disaster recovery procedures, and regularly test failure scenarios in production-like environments.

The **migration path from deprecated Spring Transaction Manager** involves transitioning from @Transactional annotations to manual transaction management, updating configuration to remove HazelcastTransactionManager dependencies, and implementing proper error handling and rollback strategies in application code.

Contemporary Hazelcast distributed transactions represent a mature, production-proven solution for coordinating operations across microservices architectures. The combination of Platform 5.x innovations like Thread-Per-Core architecture, comprehensive monitoring capabilities, and real-world implementations processing billions of transactions annually positions Hazelcast as the leading choice for mission-critical distributed transaction processing in financial services, e-commerce, and enterprise distributed systems.