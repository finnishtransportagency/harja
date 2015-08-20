ALTER TABLE sopimus ALTER urakka SET NOT NULL;
ALTER TABLE sopimus ALTER nimi SET NOT NULL;
ALTER TABLE sopimus ALTER sampoid SET NOT NULL;
ALTER TABLE sopimus ADD CONSTRAINT uniikki_sampoid UNIQUE (sampoid);