CREATE SEQUENCE expense_claim_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE expense_claim
(
    id              BIGINT DEFAULT nextval('expense_claim_seq') PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES app_user (id),
    title           VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'REIMBURSED')),
    created_at      TIMESTAMP    NOT NULL,
    submitted_at    TIMESTAMP,
    decided_at      TIMESTAMP,
    reimbursed_at   TIMESTAMP,
    decision_reason VARCHAR(1000)
);
