-- -------------------------
-- SALES SERVICE ENUMS
-- -------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'paymentmethod') THEN
CREATE TYPE paymentmethod AS ENUM (
            'CREDIT_CARD',
            'DEBIT_CARD',
            'WALLET'
        );
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticketsalestatus') THEN
CREATE TYPE ticketsalestatus AS ENUM (
            'PENDING',
            'COMPLETED',
            'FAILED',
            'REFUNDED'
        );
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'discounttype') THEN
CREATE TYPE discounttype AS ENUM (
            'PERCENTAGE',
            'FIXED'
        );
END IF;
END$$;