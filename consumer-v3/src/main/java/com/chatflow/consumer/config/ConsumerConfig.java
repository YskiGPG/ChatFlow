package com.chatflow.consumer.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConsumerConfig {

    // RabbitMQ
    private final String rabbitHost;
    private final int rabbitPort;
    private final String rabbitUser;
    private final String rabbitPassword;
    private final String rabbitVhost;
    private final int prefetchCount;
    private final int channelPoolSize;

    // MySQL
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUser;
    private final String mysqlPassword;
    private final int mysqlPoolSize;

    // Batch settings
    private final int batchSize;
    private final long flushIntervalMs;

    // Thread pool sizes
    private final int consumerThreads;
    private final int writerThreads;

    private ConsumerConfig(Properties props) {
        this.rabbitHost       = props.getProperty("rabbitmq.host", "localhost");
        this.rabbitPort       = Integer.parseInt(props.getProperty("rabbitmq.port", "5672"));
        this.rabbitUser       = props.getProperty("rabbitmq.user", "admin");
        this.rabbitPassword   = props.getProperty("rabbitmq.password", "admin123");
        this.rabbitVhost      = props.getProperty("rabbitmq.vhost", "/");
        this.prefetchCount    = Integer.parseInt(props.getProperty("rabbitmq.prefetch", "64"));
        this.channelPoolSize  = Integer.parseInt(props.getProperty("rabbitmq.channel.pool.size", "20"));

        this.mysqlHost        = props.getProperty("mysql.host", "localhost");
        this.mysqlPort        = Integer.parseInt(props.getProperty("mysql.port", "3306"));
        this.mysqlDatabase    = props.getProperty("mysql.database", "chatflow");
        this.mysqlUser        = props.getProperty("mysql.user", "chatflow");
        this.mysqlPassword    = props.getProperty("mysql.password", "chatflow123");
        this.mysqlPoolSize    = Integer.parseInt(props.getProperty("mysql.pool.size", "10"));

        this.batchSize        = Integer.parseInt(props.getProperty("batch.size", "500"));
        this.flushIntervalMs  = Long.parseLong(props.getProperty("batch.flush.interval.ms", "500"));

        this.consumerThreads  = Integer.parseInt(props.getProperty("consumer.threads", "4"));
        this.writerThreads    = Integer.parseInt(props.getProperty("writer.threads", "4"));
    }

    /**
     * Load config from classpath consumer.properties, then allow CLI args to override
     * individual keys in the form key=value.
     */
    public static ConsumerConfig load(String[] cliArgs) throws IOException {
        Properties props = new Properties();
        try (InputStream is = ConsumerConfig.class.getClassLoader()
                .getResourceAsStream("consumer.properties")) {
            if (is != null) {
                props.load(is);
            }
        }
        // CLI args override: rabbitmq.host=X mysql.host=Y ...
        for (String arg : cliArgs) {
            int eq = arg.indexOf('=');
            if (eq > 0) {
                props.setProperty(arg.substring(0, eq).trim(), arg.substring(eq + 1).trim());
            }
        }
        return new ConsumerConfig(props);
    }

    // --- Getters ---

    public String getRabbitHost()      { return rabbitHost; }
    public int    getRabbitPort()      { return rabbitPort; }
    public String getRabbitUser()      { return rabbitUser; }
    public String getRabbitPassword()  { return rabbitPassword; }
    public String getRabbitVhost()     { return rabbitVhost; }
    public int    getPrefetchCount()   { return prefetchCount; }
    public int    getChannelPoolSize() { return channelPoolSize; }

    public String getMysqlHost()       { return mysqlHost; }
    public int    getMysqlPort()       { return mysqlPort; }
    public String getMysqlDatabase()   { return mysqlDatabase; }
    public String getMysqlUser()       { return mysqlUser; }
    public String getMysqlPassword()   { return mysqlPassword; }
    public int    getMysqlPoolSize()   { return mysqlPoolSize; }

    public int    getBatchSize()       { return batchSize; }
    public long   getFlushIntervalMs() { return flushIntervalMs; }

    public int    getConsumerThreads() { return consumerThreads; }
    public int    getWriterThreads()   { return writerThreads; }

    public String getMysqlJdbcUrl() {
        return String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            mysqlHost, mysqlPort, mysqlDatabase);
    }

    @Override
    public String toString() {
        return String.format(
            "ConsumerConfig{rabbit=%s:%d, mysql=%s:%d/%s, batch=%d, flush=%dms, consumers=%d, writers=%d}",
            rabbitHost, rabbitPort, mysqlHost, mysqlPort, mysqlDatabase,
            batchSize, flushIntervalMs, consumerThreads, writerThreads);
    }
}
