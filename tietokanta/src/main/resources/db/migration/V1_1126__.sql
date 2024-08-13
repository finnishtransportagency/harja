ALTER TABLE tyomaapaivakirja_toimeksianto
    ALTER COLUMN aika DROP NOT NULL;

ALTER TABLE tyomaapaivakirja_toimeksianto
    RENAME COLUMN aika TO tuntimaara;

