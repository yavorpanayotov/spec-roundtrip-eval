CREATE SEQUENCE expense_item_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE expense_item
(
    id           BIGINT DEFAULT nextval('expense_item_seq') PRIMARY KEY,
    claim_id     BIGINT         NOT NULL REFERENCES expense_claim (id) ON DELETE CASCADE,
    category     VARCHAR(20)    NOT NULL
        CHECK (category IN ('TRAVEL', 'MEALS', 'ACCOMMODATION', 'EQUIPMENT', 'OTHER')),
    description  VARCHAR(500)   NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL CHECK (amount >= 0.01),
    expense_date DATE           NOT NULL,
    has_receipt  BOOLEAN        NOT NULL DEFAULT FALSE
);
