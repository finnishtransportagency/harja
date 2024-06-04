DO $$
    BEGIN
        IF EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'muut valaistusurakan toimenpiteet' AND enumtypid = 'suoritettavatehtava'::regtype) THEN
            ALTER TYPE suoritettavatehtava RENAME VALUE 'muut valaistusurakan toimenpiteet' TO 'muut valaistusurakoiden toimenpiteet';
        END IF;
    END $$;
