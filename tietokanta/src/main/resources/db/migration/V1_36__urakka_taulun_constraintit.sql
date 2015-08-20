ALTER TABLE urakka ALTER nimi SET NOT NULL;
ALTER TABLE urakka ALTER sampoid SET NOT NULL;
ALTER TABLE urakka ADD CONSTRAINT urakan_uniikki_sampoid UNIQUE (sampoid);