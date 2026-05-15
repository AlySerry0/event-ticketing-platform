-- -------------------------
-- BOOKING SERVICE ENUMS
-- -------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bookingstatus') THEN
CREATE TYPE bookingstatus AS ENUM (
            'PENDING',
            'CONFIRMED',
            'CHECKED_IN',
            'COMPLETED',
            'CANCELLED'
        );
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bookingitemstatus') THEN
CREATE TYPE bookingitemstatus AS ENUM (
            'RESERVED',
            'CONFIRMED',
            'REFUNDED'
        );
END IF;
END$$;