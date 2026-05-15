-- -------------------------
-- EVENT SERVICE ENUMS
-- -------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'eventcategory') THEN
CREATE TYPE eventcategory AS ENUM (
            'CONCERT',
            'SPORTS',
            'THEATER',
            'CONFERENCE',
            'FESTIVAL'
        );
END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'eventstatus') THEN
CREATE TYPE eventstatus AS ENUM (
            'UPCOMING',
            'ONGOING',
            'COMPLETED',
            'CANCELLED'
        );
END IF;
END$$;