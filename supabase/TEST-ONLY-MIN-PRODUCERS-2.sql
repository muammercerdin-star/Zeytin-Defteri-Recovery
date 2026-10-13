-- Historical test entrypoint, disabled by Work Order 08.
-- Never lower the production privacy floor. No function/table is modified.
DO $$ BEGIN
    RAISE EXCEPTION 'Test-only threshold reduction is disabled. Production requires 5 distinct producers.';
END $$;
