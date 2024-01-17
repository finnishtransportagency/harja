DO
$$
    DECLARE
        t RECORD;
        s RECORD;
        f RECORD;
    BEGIN
        FOR t IN (SELECT schemaname, tablename FROM pg_tables WHERE schemaname = 'public' and tableowner = 'flyway')
            LOOP
                EXECUTE format('GRANT ALL PRIVILEGES ON TABLE %I.%I  TO harja;', t.schemaname, t.tablename);
            END LOOP;

        FOR s IN (SELECT schemaname, sequencename FROM pg_sequences WHERE schemaname = 'public' and sequenceowner = 'flyway')
            LOOP
                EXECUTE format('GRANT ALL PRIVILEGES ON SEQUENCE %I.%I  TO harja;', s.schemaname, s.sequencename);
            END LOOP;

        FOR f IN (SELECT schemaname, funcname FROM pg_stat_user_functions WHERE schemaname = 'public' and funcname NOT ILIKE 'awsdms%')
            LOOP
                EXECUTE format('GRANT ALL PRIVILEGES ON FUNCTION %I.%I  TO harja;', f.schemaname, f.funcname);
            END LOOP;
    END
$$
