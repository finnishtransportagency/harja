CREATE UNIQUE INDEX uniikki_urakan_sampoid_paasopimuksella on sopimus (urakka_sampoid) WHERE paasopimus IS NULL;
