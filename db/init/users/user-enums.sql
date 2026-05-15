-- -------------------------
-- USER SERVICE ENUMS
-- -------------------------
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