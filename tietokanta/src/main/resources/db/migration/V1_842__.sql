-- Lisätään toteuma_materiaali taululle indeksi, jotta toteumataulun linkitys nopeutuu.
-- Jotta indeksi saadaan lisättyä muokataan toteuma_materiaalitaulun poistettu kolumni ottamaan vastaan vain false/true arvoja. Null ei ole
-- enää hyväksyttävä.
-- Sitten lisätään urakka_id, jonka varaan uusi indeksi rakennetaan ja päivitetään olemassa oleville riveille tieto omistavasta urakasta
UPDATE toteuma_materiaali set poistettu = false where poistettu is null;
ALTER TABLE toteuma_materiaali ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_materiaali ALTER COLUMN poistettu SET DEFAULT FALSE;

-- Lisää urakka_id kenttä
ALTER TABLE toteuma_materiaali ADD COLUMN urakka_id INTEGER;
CREATE INDEX CONCURRENTLY toteuma_materiaali_urakka_poistettu ON toteuma_materiaali (urakka_id, poistettu);

-- Päivitä urakat paikalleen
-- Ensimmäiset rivit ovat postgresql:n muistin lämmittämiseksi. Se nopeuttaa näiden suorittamista merkittävästi.
update toteuma_materiaali tm SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 1 offset 0) t2 WHERE tm.toteuma = t2.id;
update toteuma_materiaali tm SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 5 offset 1) t2 WHERE tm.toteuma = t2.id;
update toteuma_materiaali tm SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 10 offset 6) t2 WHERE tm.toteuma = t2.id;
update toteuma_materiaali tm SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc) t2 WHERE tm.toteuma = t2.id;