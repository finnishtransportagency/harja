-- Lisätään palvelimen versiotieto jarjestelman_tila-tauluun
-- Vaihdetaan palvelin-sarakkeen nimi selkeämmäksi
-- Lisätään uniikki-constraint palvelimen osoitteen, versiotiedon ja osa-alueen yhdistelmälle

ALTER TABLE jarjestelman_tila
    ADD COLUMN palvelimen_versio VARCHAR(255);

ALTER TABLE jarjestelman_tila
    RENAME COLUMN palvelin TO palvelimen_osoite;

ALTER TABLE jarjestelman_tila
    DROP CONSTRAINT "jarjestelman_tila_palvelin_osa-alue_key",
    ADD UNIQUE (palvelimen_osoite, palvelimen_versio, "osa-alue");

