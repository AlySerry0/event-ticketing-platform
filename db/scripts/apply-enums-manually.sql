-- =========================================================
-- Run this manually on an existing database volume
-- when you add new enum values later.
-- =========================================================

-- Example pattern:
-- ALTER TYPE userstatus ADD VALUE IF NOT EXISTS 'SUSPENDED';

-- Current milestone enum definitions:
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'userrole') THEN
CREATE TYPE userrole AS ENUM ('ATTENDEE', 'ADMIN');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'userstatus') THEN
CREATE TYPE userstatus AS ENUM ('ACTIVE', 'DEACTIVATED');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'eventcategory') THEN
CREATE TYPE eventcategory AS ENUM ('CONCERT', 'SPORTS', 'THEATER', 'CONFERENCE', 'FESTIVAL');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'eventstatus') THEN
CREATE TYPE eventstatus AS ENUM ('UPCOMING', 'ONGOING', 'COMPLETED', 'CANCELLED');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bookingstatus') THEN
CREATE TYPE bookingstatus AS ENUM ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bookingitemstatus') THEN
CREATE TYPE bookingitemstatus AS ENUM ('RESERVED', 'CONFIRMED', 'REFUNDED');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticketstatus') THEN
CREATE TYPE ticketstatus AS ENUM ('VALID', 'USED', 'EXPIRED', 'CANCELLED');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'paymentmethod') THEN
CREATE TYPE paymentmethod AS ENUM ('CREDIT_CARD', 'DEBIT_CARD', 'WALLET');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticketsalestatus') THEN
CREATE TYPE ticketsalestatus AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED');
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'discounttype') THEN
CREATE TYPE discounttype AS ENUM ('PERCENTAGE', 'FIXED');
END IF;
END$$;