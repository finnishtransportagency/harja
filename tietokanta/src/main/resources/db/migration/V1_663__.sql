-- Specql bugitti, eikä päivitys onnistunut datalle {:toimenpide :avaus :palvelumuoto :jotain} -> {:toimenpide :ei-avausta :palvelumuoto nil}
-- Jos specql saadaan päivitettyä, voi kokeilla palauttaa tämän constraintin. Vaikka eipä tämä kauhean tärkeä taida olla
ALTER TABLE kan_liikennetapahtuma_toiminto DROP CONSTRAINT avaamattomalla_sillalla_ei_palvelumuotoa;
