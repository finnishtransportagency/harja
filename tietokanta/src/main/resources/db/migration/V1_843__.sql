-- Lisätään toteuma_materiaali taululle indeksi, jotta toteumataulun linkitys nopeutuu.
-- Jotta indeksi saadaan lisättyä muokataan toteuma_materiaalitaulun poistettu kolumni ottamaan vastaan vain false/true arvoja. Null ei ole
-- enää hyväksyttävä.
-- Sitten lisätään urakka_id, jonka varaan uusi indeksi rakennetaan ja päivitetään olemassa oleville riveille tieto omistavasta urakasta
UPDATE toteuma_materiaali set poistettu = false where poistettu is null;
ALTER TABLE toteuma_materiaali ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_materiaali ALTER COLUMN poistettu SET DEFAULT FALSE;

-- Lisää urakka_id kenttä
ALTER TABLE toteuma_materiaali ADD COLUMN urakka_id INTEGER;
