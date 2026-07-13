package com.example.expenses.db;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;

/** Creates an H2 datasource, applies the Flyway migrations, hands out a jOOQ context. */
public final class Database {

    private Database() {
    }

    public static DSLContext connect(String jdbcUrl) {
        DataSource dataSource = JdbcConnectionPool.create(jdbcUrl, "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return DSL.using(dataSource, SQLDialect.H2);
    }
}
