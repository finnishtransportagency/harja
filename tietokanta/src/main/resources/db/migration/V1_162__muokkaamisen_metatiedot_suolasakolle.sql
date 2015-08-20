ALTER TABLE suolasakko ADD COLUMN muokattu TIMESTAMP;
ALTER TABLE suolasakko ADD COLUMN muokkaaja INTEGER;
ALTER TABLE suolasakko ADD COLUMN luotu TIMESTAMP;
ALTER TABLE suolasakko ADD COLUMN luoja INTEGER;

ALTER TABLE suolasakko
            ADD CONSTRAINT uniikki_suolasakko
            UNIQUE (urakka, hoitokauden_alkuvuosi);