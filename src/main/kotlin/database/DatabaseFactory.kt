package com.example.database

import com.example.config.DbConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

object DatabaseFactory {

    @Volatile
    private var dataSource: DataSource? = null

    fun init(cfg: DbConfig) {
        val ds = HikariDataSource(
            HikariConfig().apply {
                driverClassName = cfg.driver
                jdbcUrl = cfg.url
                username = cfg.user
                password = cfg.password
                maximumPoolSize = 10
                minimumIdle = 1
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            }
        )
        dataSource = ds

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()

        Database.connect(ds)
    }

    fun close() {
        (dataSource as? HikariDataSource)?.close()
        dataSource = null
    }

    fun isHealthy(): Boolean {
        val ds = dataSource ?: return false
        return runCatching {
            ds.connection.use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT 1").use { it.next() }
                }
            }
        }.getOrDefault(false)
    }
}
