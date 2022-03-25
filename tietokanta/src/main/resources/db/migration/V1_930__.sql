ALTER TABLE urakka ADD velho_oid varchar(128);
CREATE UNIQUE INDEX urakka_unique_velho_oid ON urakka(velho_oid);