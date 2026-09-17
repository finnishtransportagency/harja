-- Lisätään liikennevahinkobonus erilliskustannusten tyyppien joukkoon.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_enum
         WHERE enumlabel = 'liikennevahinkojen_aiheuttajien_selvitysbonus'
           AND enumtypid = 'erilliskustannustyyppi'::regtype
    ) THEN
        ALTER TYPE erilliskustannustyyppi
            ADD VALUE 'liikennevahinkojen_aiheuttajien_selvitysbonus';
    END IF;
END $$;
