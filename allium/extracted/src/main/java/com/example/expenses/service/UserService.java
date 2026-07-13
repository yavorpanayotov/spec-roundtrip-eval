package com.example.expenses.service;

import com.example.expenses.jooq.tables.records.AppUserRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.expenses.jooq.Tables.APP_USER;

@Service
public class UserService {

    private final DSLContext dsl;

    public UserService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<AppUserRecord> allUsers() {
        return dsl.fetch(APP_USER).sortAsc(APP_USER.NAME);
    }
}
