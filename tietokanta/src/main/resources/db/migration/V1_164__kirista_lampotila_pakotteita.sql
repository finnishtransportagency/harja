   ALTER TABLE lampotilat
ADD CONSTRAINT uniikki_urakka_hk_lampotila UNIQUE (urakka, alkupvm, loppupvm);