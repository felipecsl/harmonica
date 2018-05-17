package com.improve_future.harmonica.core

import org.jetbrains.exposed.sql.Database
import java.io.Closeable
import java.sql.*
import javax.naming.InitialContext
import javax.sql.DataSource

open class Connection(
        private val config: DbConfig
): Closeable {
    val javaConnection: java.sql.Connection
    val database: Database

    override fun close() {
        if (!isClosed) javaConnection.close()
//        DriverManager.deregisterDriver(DriverManager.getDriver(
//                buildConnectionUriFromDbConfig(config)))
    }

    val isClosed: Boolean
    get() { return javaConnection.isClosed }

    init {
        val ds = InitialContext().lookup(
                config.toConnectionUrlString()) as DataSource
//        javaConnection = object : java.sql.Connection by ds.connection {
//            override fun setTransactionIsolation(level: Int) {}
//        }
//        DriverManager.registerDriver(
//                DriverManager.getDriver(buildConnectionUriFromDbConfig(config)))
        javaConnection = object : java.sql.Connection by DriverManager.getConnection(
                buildConnectionUriFromDbConfig(config),
                config.user,
                config.password
        ) {
            override fun setTransactionIsolation(level: Int) {}
        }

        if (config.dbms == Dbms.Oracle)
            execute("SELECT 1 FROM DUAL;")
        else
            execute("SELECT 1;")
        javaConnection.autoCommit = false
        database = Database.connect({ javaConnection })
    }

    companion object {
        fun create(block: DbConfig.() -> Unit): Connection {
            return Connection(DbConfig.create(block))
        }

        private fun buildConnectionUriFromDbConfig(dbConfig: DbConfig): String {
            return dbConfig.run {
                when (dbms) {
                    Dbms.PostgreSQL ->
                        "jdbc:postgresql://$host:$port/$dbName?autoReconnect=true"
                    Dbms.MySQL ->
                        "jdbc:mysql://$host:$port/$dbName?autoReconnect=true"
                    Dbms.SQLite ->
                        ""
                    Dbms.Oracle ->
                            ""
                }
            }
        }
    }

    open fun transaction(block: Connection.() -> Unit) {
        org.jetbrains.exposed.sql.transactions.transaction {
            try {
                block()
                javaConnection.commit()
            } catch (e: Exception) {
                javaConnection.rollback()
                throw e
            }
        }
    }

    fun execAndClose(block: (Connection) -> Unit) {
        block(this)
        this.close()
    }

    fun executeSelect(sql: String) {
        val statement = createStatement()
        val rs = statement.executeQuery(sql)
        while (rs.next()) {
            for (i in 0 until rs.metaData.columnCount - 1) {
                when (rs.metaData.getColumnType(i)) {
                    Types.DATE -> {}
                    Types.BIGINT -> {}
                    Types.BINARY -> {}
                    Types.BIT -> {}
                }
            }
        }
        rs.close()
    }

    /**
     * Execute SQL
     */
    fun execute(sql: String): Boolean {
        val statement = createStatement()
        val result: Boolean
        result = statement.execute(sql)
        statement.close()
        return result
    }

    fun doesTableExist(tableName: String): Boolean {
        val resultSet = javaConnection.metaData.getTables(
                null, null, tableName, null)
        val result = resultSet.next()
        resultSet.close()
        return result
    }

    fun createStatement(): Statement {
        return javaConnection.createStatement()
    }
}