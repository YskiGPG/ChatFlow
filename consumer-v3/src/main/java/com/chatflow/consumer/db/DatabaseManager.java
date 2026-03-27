package com.chatflow.consumer.db;

import com.chatflow.consumer.config.ConsumerConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final HikariDataSource dataSource;

    public DatabaseManager(ConsumerConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getMysqlJdbcUrl());
        hikari.setUsername(config.getMysqlUser());
        hikari.setPassword(config.getMysqlPassword());
        hikari.setMaximumPoolSize(config.getMysqlPoolSize());
        hikari.setMinimumIdle(2);
        hikari.setConnectionTimeout(30_000);
        hikari.setIdleTimeout(600_000);
        hikari.setMaxLifetime(1_800_000);
        hikari.setPoolName("ChatFlowConsumerPool");
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikari);
        log.info("HikariCP pool initialized: {}", config.getMysqlJdbcUrl());
    }

    /** Constructor for testing — accepts a pre-configured HikariDataSource. */
    public DatabaseManager(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("HikariCP pool shut down.");
        }
    }
}
