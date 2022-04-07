ALTER TABLE urakka ADD velho_oid TEXT;
CREATE UNIQUE INDEX urakka_unique_velho_oid ON urakka(velho_oid);
ALTER TABLE urakka ADD CONSTRAINT velho_oid_ehdot
    CHECK (velho_oid IS NULL OR tyyppi IN ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi));