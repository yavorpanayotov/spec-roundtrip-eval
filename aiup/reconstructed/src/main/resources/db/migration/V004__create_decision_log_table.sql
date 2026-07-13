CREATE SEQUENCE decision_log_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE decision_log
(
    id          BIGINT DEFAULT nextval('decision_log_seq') PRIMARY KEY,
    claim_id    BIGINT      NOT NULL REFERENCES expense_claim (id) ON DELETE CASCADE,
    actor_id    BIGINT      NOT NULL REFERENCES app_user (id),
    action      VARCHAR(20) NOT NULL
        CHECK (action IN ('SUBMITTED', 'WITHDRAWN', 'APPROVED', 'REJECTED', 'REIMBURSED')),
    occurred_at TIMESTAMP   NOT NULL,
    reason      VARCHAR(1000)
);
