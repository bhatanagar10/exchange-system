package com.mine.engine.service;

import com.mine.engine.config.KafkaConfig;
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
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

    private static final String SNAPSHOT_FILE = "stockData.snapshot";
    private static final String SNAPSHOT_DIRECTORY = "C:\\Users\\INNOVATIONM-ADMIN\\Documents\\mine\\exchange\\engine";

    public SnapshotService(
            StockDataService stockDataService,
            @Value("${snapshot.enabled:true}") boolean snapshotEnabled,
            @Autowired(required = false) KafkaOffsetTracker offsetTracker,
            @Autowired(required = false) StockService stockService,
            @Autowired(required = false) ConsumerFactory<String, Message> consumerFactory,
            @Value("${spring.kafka.bootstrap-servers:localhost:9072}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:engine-order-consumer-group}") String groupId) {
        this.stockDataService = stockDataService;
        this.snapshotEnabled = snapshotEnabled;
        this.offsetTracker = offsetTracker;
        this.stockService = stockService;
        this.consumerFactory = consumerFactory;
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.snapshotPath = Paths.get(SNAPSHOT_DIRECTORY);

        // Create snapshot directory if it doesn't exist
        createSnapshotDirectory();

        log.info("SnapshotService initialized - Path: {}, Enabled: {}, KafkaOffsetTracker: {}, StockService: {}, ConsumerFactory: {}",
                snapshotPath, snapshotEnabled, offsetTracker != null ? "available" : "not available",
                stockService != null ? "available" : "not available",
                consumerFactory != null ? "available" : "not available");
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
    @Scheduled(fixedRate = 10000) // 10 seconds = 10000 milliseconds
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
                log.debug("Including Kafka offsets in snapshot: {}", kafkaOffsets);
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

            long duration = System.currentTimeMillis() - startTime;
            log.info("Snapshot created in {}ms - File: {}, Markets: {}, Kafka offsets: {}",
                    duration, snapshotFile, snapshotData.size(),
                    kafkaOffsets != null ? kafkaOffsets.size() + " partitions" : "none");

        } catch (Exception e) {
            log.error("Error creating snapshot", e);
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

            if (!Files.exists(snapshotFile)) {
                log.info("No snapshot file found at: {}", snapshotFile);
                return null;
            }

            long startTime = System.currentTimeMillis();

            // Read binary snapshot (fastest read - no decompression)
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(
                            Files.newInputStream(snapshotFile)))) {
                Object snapshotObject = ois.readObject();

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
                log.info("Snapshot loaded in {}ms from: {}, Markets: {}, Kafka offsets: {}",
                        duration, snapshotFile, snapshot.size(),
                        kafkaOffsets != null ? kafkaOffsets.size() + " partitions" : "none");

                // Replay messages from Kafka after the snapshot offsets to rebuild the orderbook
                if (kafkaOffsets != null && !kafkaOffsets.isEmpty() && stockService != null && consumerFactory != null) {
                    log.info("Starting Kafka message replay after snapshot restoration...");
                    replayMessagesFromKafka(kafkaOffsets);
                }

                return snapshot;
            }

        } catch (Exception e) {
            log.error("Error loading snapshot", e);
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
                        switch(message.getOrderType()) {
                            case BUY:
                                stockService.placeBuyOrder(message.getUserId(), message.getPrice(), 
                                        message.getQuantity(), message.getOrderExecutionType(), message.getTimestamp());
                                break;
                            case SELL:
                                stockService.placeSellOrder(message.getUserId(), message.getPrice(), 
                                        message.getQuantity(), message.getOrderExecutionType(), message.getTimestamp());
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
