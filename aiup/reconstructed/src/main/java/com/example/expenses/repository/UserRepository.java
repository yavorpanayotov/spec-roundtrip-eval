package com.example.expenses.repository;

import static com.example.expenses.jooq.Tables.APP_USER;

import com.example.expenses.domain.AppUser;
import com.example.expenses.domain.Role;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final DSLContext ctx;

    public UserRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    public List<AppUser> findAll() {
        return ctx
                .select(APP_USER.ID, APP_USER.NAME, APP_USER.EMAIL,
                        APP_USER.ROLE.convertFrom(Role::valueOf))
                .from(APP_USER)
                .orderBy(APP_USER.NAME)
                .fetch(Records.mapping(AppUser::new));
    }

    public Optional<AppUser> findById(long id) {
        return ctx
                .select(APP_USER.ID, APP_USER.NAME, APP_USER.EMAIL,
                        APP_USER.ROLE.convertFrom(Role::valueOf))
                .from(APP_USER)
                .where(APP_USER.ID.eq(id))
                .fetchOptional(Records.mapping(AppUser::new));
    }
}
