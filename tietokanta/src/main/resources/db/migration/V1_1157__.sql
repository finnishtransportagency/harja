-- Siirretään talvihoitoreitin kalustot reitille kuuluvaksi
ALTER TABLE talvihoitoreitti
    ADD COLUMN tr_maara  INTEGER,
    ADD COLUMN ka_maara  INTEGER,
    ADD COLUMN kup_maara INTEGER;

-- Poista vanhat kalustot - Tämä on vielä mahdollista, koska ominaisuutta ei ole julkaistu.
DROP TABLE talvihoitoreitti_sijainti_kalusto;
