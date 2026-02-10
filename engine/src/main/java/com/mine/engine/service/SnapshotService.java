package com.mine.engine.service;

import com.mine.engine.config.KafkaConfig;
import com.mine.engine.config.S3SnapshotProperties;
import com.mine.engine.model.Market;
import com.mine.engine.model.Message;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.PutObjectRequest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.PriorityQueue;

/**
 * SnapshotService - Creates fast binary snapshots of stockData for quick recovery
 * Uses binary serialization for fastest read/write performance
 * Snapshots stored every 10 seconds
 * Also stores Kafka offsets when Kafka is the messaging provider
 */
@Slf4j
@Service
public class SnapshotService {

    private final StockDataService stockDataService;
    private final KafkaOffsetTracker offsetTracker; // Optional - only used if Kafka is enabled
    private final StockService stockService; // Optional - only used if Kafka is enabled for replay
    private final ConsumerFactory<String, Message> consumerFactory; // Optional - only used if Kafka is enabled

    private final Path snapshotPath;
    private final boolean snapshotEnabled;
    private final String bootstrapServers;
    private final String groupId;

    private final S3SnapshotProperties s3Properties;
    private final AmazonS3 s3Client; // null when S3 disabled/misconfigured

    private static final String SNAPSHOT_FILE = "stockData.snapshot";

    public SnapshotService(
            StockDataService stockDataService,
            @Value("${snapshot.enabled:true}") boolean snapshotEnabled,
            @Value("${snapshot.path:data/snapshots}") String snapshotDirectory,
            @Autowired(required = false) KafkaOffsetTracker offsetTracker,
            @Autowired(required = false) StockService stockService,
            @Autowired(required = false) ConsumerFactory<String, Message> consumerFactory,
            @Value("${spring.kafka.bootstrap-servers:localhost:9072}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:engine-order-consumer-group}") String groupId,
            S3SnapshotProperties s3Properties
    ) {
        this.stockDataService = stockDataService;
        this.snapshotEnabled = snapshotEnabled;
        this.offsetTracker = offsetTracker;
        this.stockService = stockService;
        this.consumerFactory = consumerFactory;
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.snapshotPath = Paths.get(snapshotDirectory);
        this.s3Properties = s3Properties;

        // Normalize S3 key - use default if empty
        if (s3Properties.getKey() == null || s3Properties.getKey().isBlank()) {
            s3Properties.setKey("engine/" + SNAPSHOT_FILE);
        } else {
            s3Properties.setKey(s3Properties.getKey().trim());
        }

        this.s3Client = buildS3ClientIfEnabled();

        // Create snapshot directory if it doesn't exist
        createSnapshotDirectory();

        log.info("SnapshotService initialized - Path: {}, Enabled: {}, S3 enabled: {}, S3 prefer: {}, S3 bucket: {}, S3 key: {}, KafkaOffsetTracker: {}, StockService: {}, ConsumerFactory: {}",
                snapshotPath, snapshotEnabled, (this.s3Client != null), s3Properties.isPrefer(), 
                (s3Properties.getBucket() == null || s3Properties.getBucket().isBlank() ? "(not set)" : s3Properties.getBucket()), 
                s3Properties.getKey(),
                offsetTracker != null ? "available" : "not available",
                stockService != null ? "available" : "not available",
                consumerFactory != null ? "available" : "not available");
    }

    private AmazonS3 buildS3ClientIfEnabled() {

        try {
            AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
            
            // Set credentials
            String accessKey = s3Properties.getAccessKey();
            String secretKey = s3Properties.getSecretKey();
            if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
                AWSCredentials credentials = new BasicAWSCredentials(accessKey.trim(), secretKey.trim());
                builder.withCredentials(new AWSStaticCredentialsProvider(credentials));
            } else {
                builder.withCredentials(new DefaultAWSCredentialsProviderChain());
            }

            // Set region or endpoint
            String regionStr = s3Properties.getRegion() != null && !s3Properties.getRegion().isBlank() 
                    ? s3Properties.getRegion().trim() : "us-east-1";
            String endpoint = s3Properties.getEndpoint();
            if (endpoint != null && !endpoint.isBlank()) {
                // Custom endpoint (e.g., MinIO, LocalStack)
                builder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(endpoint.trim(), regionStr)
                );
                builder.withPathStyleAccessEnabled(s3Properties.isPathStyleAccess());
            } else {
                // Standard AWS S3
                builder.withRegion(regionStr);
                if (s3Properties.isPathStyleAccess()) {
                    builder.withPathStyleAccessEnabled(true);
                }
            }

            return builder.build();
        } catch (Exception e) {
            log.error("S3 snapshot is enabled but failed to initialize S3 client. S3 snapshot features will be disabled.", e);
            return null;
        }
    }
    /**
     * Create snapshot directory if it doesn't exist
     */
    private void createSnapshotDirectory() {
        try {
            if (!Files.exists(snapshotPath)) {
                Files.createDirectories(snapshotPath);
                log.info("Created snapshot directory: {}", snapshotPath);
            }
        } catch (IOException e) {
            log.error("Failed to create snapshot directory: {}", snapshotPath, e);
        }
    }

    /**
     * Scheduled snapshot - runs every 10 seconds
     * Creates binary snapshot for fastest recovery
     * Includes Kafka offsets if KafkaOffsetTracker is available
     */
    @Scheduled(fixedRateString = "#{${snapshot.interval.seconds:10} * 1000}")
    public void createSnapshot() {
        log.info("Starting snapshot creation...");
        if (!snapshotEnabled) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Create deep copy of stockData for snapshot (thread-safe)
            // Converts PriorityQueues to Lists for reliable serialization
            Map<Market, SerializableOrderBook> snapshotData = createSnapshotCopy();

            // Get Kafka offsets if available
            Map<Integer, Long> kafkaOffsets = null;
            if (offsetTracker != null) {
                kafkaOffsets = offsetTracker.getAllOffsets();
                log.info("Including Kafka offsets in snapshot: {}", kafkaOffsets);
            }

            // Create snapshot wrapper with order book data and offsets
            SnapshotData snapshotWrapper = new SnapshotData(snapshotData, kafkaOffsets);

            // Write to binary file (fastest format - no compression)
            Path snapshotFile = snapshotPath.resolve(SNAPSHOT_FILE);

            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(snapshotFile)))) {
                oos.writeObject(snapshotWrapper);
                oos.flush();
            }

            // Upload to S3 (optional)
            uploadSnapshotToS3IfConfigured(snapshotFile);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Snapshot created in {}ms - File: {}, Markets: {}, Kafka offsets: {}",
                    duration, snapshotFile, snapshotData.size(),
                    kafkaOffsets != null ? kafkaOffsets.size() + " partitions" : "none");

        } catch (Exception e) {
            log.error("Error creating snapshot", e);
        }
    }

    private void uploadSnapshotToS3IfConfigured(Path snapshotFile) {
        if (s3Client == null) return;
        String bucket = s3Properties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            log.error("snapshot.s3.enabled=true but snapshot.s3.bucket is empty. Skipping S3 upload.");
            return;
        }

        try {
            PutObjectRequest req = new PutObjectRequest(bucket, s3Properties.getKey(), snapshotFile.toFile());
            req.setMetadata(new com.amazonaws.services.s3.model.ObjectMetadata());
            req.getMetadata().setContentType("application/octet-stream");

            long start = System.currentTimeMillis();
            s3Client.putObject(req);
            long duration = System.currentTimeMillis() - start;
            log.info("Uploaded snapshot to S3 in {}ms - s3://{}/{}", duration, bucket, s3Properties.getKey());
        } catch (Exception e) {
            log.error("Failed to upload snapshot to S3 (will keep local snapshot): s3://{}/{}", bucket, s3Properties.getKey(), e);
        }
    }

    /**
     * Create a deep copy of stockData for snapshot (thread-safe)
     * Converts PriorityQueues to Lists for reliable serialization
     */
    private Map<Market, SerializableOrderBook> createSnapshotCopy() {
        Map<Market, SerializableOrderBook> copy = new HashMap<>();

        // Create deep copy of each OrderBook
        for (Map.Entry<Market, OrderBook> entry : stockDataService.getAllEntries()) {
            Market market = entry.getKey();
            OrderBook originalOrderBook = entry.getValue();

            // Convert PriorityQueues to Lists for serialization
            List<Order> buyOrdersList = new ArrayList<>(originalOrderBook.getBuyOrders());
            List<Order> sellOrdersList = new ArrayList<>(originalOrderBook.getSellOrders());

            copy.put(market, new SerializableOrderBook(buyOrdersList, sellOrdersList));
        }

        return copy;
    }

    /**
     * Load snapshot from disk - fastest binary read
     * Also restores Kafka offsets if available in snapshot and KafkaOffsetTracker is available
     *
     * @return Map<Market, OrderBook> if found, null otherwise
     */
    @SuppressWarnings("unchecked")
    public Map<Market, OrderBook> loadSnapshot() {
        try {
            Path snapshotFile = snapshotPath.resolve(SNAPSHOT_FILE);

            long startTime = System.currentTimeMillis();

            Object snapshotObject = null;
            boolean loadedFromS3 = false;

            // Prefer S3 if configured, otherwise fall back to local
            if (s3Properties.isPrefer() && s3Client != null) {
                snapshotObject = loadSnapshotObjectFromS3();
                loadedFromS3 = (snapshotObject != null);
            }

            if (snapshotObject == null) {
                if (Files.exists(snapshotFile)) {
                    // Read binary snapshot from local disk (fastest)
                    try (ObjectInputStream ois = new ObjectInputStream(
                            new BufferedInputStream(
                                    Files.newInputStream(snapshotFile)))) {
                        snapshotObject = ois.readObject();
                    }
                } else if (s3Client != null) {
                    // If local file doesn't exist, try S3 as a fallback
                    snapshotObject = loadSnapshotObjectFromS3();
                    loadedFromS3 = (snapshotObject != null);
                }
            }

            if (snapshotObject == null) {
                String s3Location = s3Client != null 
                        ? ("s3://" + s3Properties.getBucket() + "/" + s3Properties.getKey()) 
                        : "disabled";
                log.info("No snapshot found (local file missing and/or S3 unavailable). Local path: {}, S3: {}",
                        snapshotFile, s3Location);
                return null;
            }

                Map<Market, SerializableOrderBook> serializedSnapshot;
                Map<Integer, Long> kafkaOffsets;

                // Handle both old format (Map only) and new format (SnapshotData wrapper)
                SnapshotData snapshotData = (SnapshotData) snapshotObject;
                serializedSnapshot = snapshotData.getOrderBookData();
                kafkaOffsets = snapshotData.getKafkaOffsets();

                // Restore Kafka offsets if available
                if (kafkaOffsets != null && offsetTracker != null) {
                    offsetTracker.restoreOffsets(kafkaOffsets);
                    log.info("Restored Kafka offsets from snapshot: {}", kafkaOffsets);
                } else if (kafkaOffsets != null) {
                    log.warn("Snapshot contains Kafka offsets but KafkaOffsetTracker is not available");
                }


                // Convert back to OrderBook with PriorityQueues
                Map<Market, OrderBook> snapshot = convertToOrderBookMap(serializedSnapshot);

                long duration = System.currentTimeMillis() - startTime;
                String source = loadedFromS3 
                        ? ("s3://" + s3Properties.getBucket() + "/" + s3Properties.getKey()) 
                        : snapshotFile.toString();
                log.info("Snapshot loaded in {}ms from: {}, Markets: {}, Kafka offsets: {}",
                        duration, source, snapshot.size(),
                        kafkaOffsets != null ? kafkaOffsets.size() + " partitions" : "none");

                // Replay messages from Kafka after the snapshot offsets to rebuild the orderbook
                if (kafkaOffsets != null && !kafkaOffsets.isEmpty() && stockService != null && consumerFactory != null) {
                    log.info("Starting Kafka message replay after snapshot restoration...");
                    replayMessagesFromKafka(kafkaOffsets);
                }

                return snapshot;

        } catch (Exception e) {
            log.error("Error loading snapshot", e);
            return null;
        }
    }

    private Object loadSnapshotObjectFromS3() {
        if (s3Client == null) return null;
        String bucket = s3Properties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            log.warn("snapshot.s3.enabled=true but snapshot.s3.bucket is empty. Skipping S3 download.");
            return null;
        }

        try {
            GetObjectRequest req = new GetObjectRequest(bucket, s3Properties.getKey());

            long start = System.currentTimeMillis();
            S3Object s3Object = s3Client.getObject(req);
            try (S3ObjectInputStream s3is = s3Object.getObjectContent();
                 InputStream bis = new BufferedInputStream(s3is)) {
                
                byte[] bytes = bis.readAllBytes();
                long duration = System.currentTimeMillis() - start;
                log.info("Downloaded snapshot from S3 in {}ms - s3://{}/{} ({} bytes)", duration, bucket, s3Properties.getKey(), bytes.length);

                try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
                    return ois.readObject();
                }
            }
        } catch (com.amazonaws.services.s3.model.AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                log.info("No snapshot object found in S3 at s3://{}/{}", bucket, s3Properties.getKey());
            } else {
                log.error("S3 error while downloading snapshot: s3://{}/{}", bucket, s3Properties.getKey(), e);
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to download/load snapshot from S3: s3://{}/{}", bucket, s3Properties.getKey(), e);
            return null;
        }
    }

    /**
     * Replay messages from Kafka starting from the saved offset to rebuild the orderbook
     * This processes all messages that arrived after the snapshot was taken
     * Assumes single partition (partition 0)
     * 
     * @param savedOffsets Map of partition -> last processed offset from snapshot
     */
    private void replayMessagesFromKafka(Map<Integer, Long> savedOffsets) {
        if (savedOffsets == null || savedOffsets.isEmpty()) {
            log.info("No offsets to replay from");
            return;
        }

        // Get offset for partition 0 (single partition)
        Long savedOffset = savedOffsets.get(0);
        if (savedOffset == null) {
            log.warn("No offset found for partition 0, skipping replay");
            return;
        }

        KafkaConsumer<String, Message> consumer = null;
        try {
            // Create a consumer for replay (use a unique group-id to avoid conflicts)
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            // Use a unique group-id for replay to avoid interfering with the main consumer
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-replay");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
            props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
            props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");
            props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Message.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            // Don't auto-commit during replay
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            
            consumer = new KafkaConsumer<>(props, new StringDeserializer(), 
                    new JsonDeserializer<>(Message.class, false));
            
            // Assign partition 0 (single partition)
            TopicPartition partition = new TopicPartition(KafkaConfig.ORDER_EVENTS_TOPIC, 0);
            consumer.assign(Collections.singletonList(partition));
            
            // Get end offset from partition (latest available message)
            // This is independent of consumer groups - it's the actual last message in the partition
            Long endOffset = consumer.endOffsets(Collections.singletonList(partition)).get(partition);
            if (endOffset == null) {
                log.warn("Could not determine end offset for partition 0");
                return;
            }
            
            log.info("Replay will process messages up to partition end offset: {}", endOffset);
            
            // Start from offset + 1 (since offset represents the last processed message)
            long startOffset = savedOffset + 1;
            
            // If start offset is already at or beyond end offset, nothing to replay
            if (startOffset >= endOffset) {
                log.info("No messages to replay - start offset {} >= end offset {}", startOffset, endOffset);
                return;
            }
            
            // Seek to start offset
            consumer.seek(partition, startOffset);
            log.info("Replaying messages from partition 0, offset {} to {}", startOffset, endOffset - 1);
            
            int totalReplayed = 0;
            
            // Poll and process messages until caught up to end offset
            while (true) {
                ConsumerRecords<String, Message> records = consumer.poll(Duration.ofMillis(1000));
                
                // Process each record
                for (ConsumerRecord<String, Message> record : records) {
                    Message message = record.value();
                    long offset = record.offset();
                    
                    try {
                        log.debug("Replaying message from partition 0 offset {}: {}", offset, message);
                        
                        // Process message using StockService (same logic as KafkaMessageConsumer)
                        // For replay, we skip idempotency check since these are historical messages
                        String idempotencyKey = message.getIdempotencyKey();
                        switch(message.getOrderType()) {
                            case BUY:
                                stockService.placeBuyOrder(message.getUserId(), message.getPrice(), 
                                        message.getQuantity(), message.getOrderExecutionType(), message.getTimestamp(), idempotencyKey);
                                break;
                            case SELL:
                                stockService.placeSellOrder(message.getUserId(), message.getPrice(), 
                                        message.getQuantity(), message.getOrderExecutionType(), message.getTimestamp(), idempotencyKey);
                                break;
                            default:
                                log.warn("Unknown order type in replayed message: {}", message.getOrderType());
                        }
                        
                        // Update offset tracker
                        if (offsetTracker != null) {
                            offsetTracker.updateOffset(0, offset);
                        }
                        
                        totalReplayed++;
                        
                    } catch (Exception e) {
                        log.error("Error processing replayed message from partition 0 offset {}: {}", 
                                offset, e.getMessage(), e);
                        // Continue processing other messages even if one fails
                    }
                }
                
                // Check if we're caught up (current position >= end offset)
                long currentPosition = consumer.position(partition);
                if (currentPosition >= endOffset) {
                    log.info("Reached end offset {} - replay complete", endOffset);
                    break;
                }
            }
            
            log.info("Kafka message replay completed. Replayed {} messages from offset {} to {}", 
                    totalReplayed, startOffset, endOffset - 1);
            
        } catch (Exception e) {
            log.error("Error during Kafka message replay", e);
        } finally {
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    log.warn("Error closing Kafka consumer during replay", e);
                }
            }
        }
    }

    /**
     * Convert SerializableOrderBook map to OrderBook map with PriorityQueues
     */
    private Map<Market, OrderBook> convertToOrderBookMap(Map<Market, SerializableOrderBook> serialized) {
        Map<Market, OrderBook> result = new HashMap<>();

        for (Map.Entry<Market, SerializableOrderBook> entry : serialized.entrySet()) {
            Market market = entry.getKey();
            SerializableOrderBook serializedOrderBook = entry.getValue();

            OrderBook orderBook = new OrderBook();

            // Convert Lists back to PriorityQueues
            PriorityQueue<Order> buyOrders = new PriorityQueue<>(
                    Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getId));
            buyOrders.addAll(serializedOrderBook.getBuyOrders());
            orderBook.setBuyOrders(buyOrders);

            PriorityQueue<Order> sellOrders = new PriorityQueue<>(
                    Comparator.comparing(Order::getPrice).thenComparing(Order::getId));
            sellOrders.addAll(serializedOrderBook.getSellOrders());
            orderBook.setSellOrders(sellOrders);

            result.put(market, orderBook);
        }

        return result;
    }

    /**
     * Serializable wrapper for snapshot data including order books and Kafka offsets
     */
    @Getter
    private static class SnapshotData implements Serializable {
        private static final long serialVersionUID = 1L;

        private Map<Market, SerializableOrderBook> orderBookData;
        private Map<Integer, Long> kafkaOffsets; // Partition -> Offset mapping

        public SnapshotData(Map<Market, SerializableOrderBook> orderBookData, Map<Integer, Long> kafkaOffsets) {
            this.orderBookData = new HashMap<>(orderBookData);
            this.kafkaOffsets = kafkaOffsets != null ? new HashMap<>(kafkaOffsets) : null;
        }
    }

    /**
     * Serializable wrapper for OrderBook data (uses Lists instead of PriorityQueues)
     * This ensures fast and reliable binary serialization
     */
    @Getter
    private static class SerializableOrderBook implements Serializable {
        private static final long serialVersionUID = 1L;

        private List<Order> buyOrders;
        private List<Order> sellOrders;

        public SerializableOrderBook(List<Order> buyOrders, List<Order> sellOrders) {
            this.buyOrders = new ArrayList<>(buyOrders);
            this.sellOrders = new ArrayList<>(sellOrders);
        }
    }
}
