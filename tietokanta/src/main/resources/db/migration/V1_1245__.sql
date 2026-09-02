-- Poista käyttämättömäksi jääneet edelinen_maara ja uusi_maara sarakkeet mhu_muutos_tehtava_ja_maaraluettelo taulusta

ALTER TABLE mhu_muutos_tehtava_ja_maaraluettelo
    DROP COLUMN edellinen_maara,
    DROP COLUMN uusi_maara;

ALTER TABLE mhu_muutos_tehtava_ja_maaraluettelo_historia
    DROP COLUMN edellinen_maara,
    DROP COLUMN uusi_maara;
