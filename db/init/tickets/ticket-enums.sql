-- -------------------------
-- TICKET SERVICE ENUMS
-- -------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticketstatus') THEN
CREATE TYPE ticketstatus AS ENUM (
            'VALID',
            'USED',
            'EXPIRED',
            'CANCELLED'
        );
END IF;
END$$;
