<<<<<<< HEAD
ALTER TABLE turvallisuuspoikkeama
ADD COLUMN lahetetty TIMESTAMP,
ADD COLUMN lahetys_onnistunut BOOLEAN;

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('turi', 'laheta-turvallisuuspoikkeama');
=======
-- Lisää unohtuneet luojan (käyttäjä) foreign keyt
ALTER TABLE paivystys ADD CONSTRAINT paivystys_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id);
ALTER TABLE yhteyshenkilo ADD CONSTRAINT yhteyshenkilo_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id);
ALTER TABLE yhteyshenkilo_urakka ADD CONSTRAINT yhteyshenkilo_urakka_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id);
ALTER TABLE organisaatio ADD CONSTRAINT organisaatio_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id);

-- Lisää sanktio tauluun ulkoinen_id ja vaadi uniikki (luoja, ulkoinen_id) (konversioita varten)
ALTER TABLE sanktio ADD COLUMN ulkoinen_id INTEGER;
ALTER TABLE sanktio ADD CONSTRAINT uniikki_ulkoinen_sanktio UNIQUE (ulkoinen_id, luoja);

-- Jotta AURA-konversio onnistuu, on varmistettava että nämä mäppäytyvät Harjan sanktiotyypeiksi:
-- SELECT  DISTINCT  tehtavakokonaisuus from sanktio;
INSERT
INTO sanktiotyyppi
(nimi, sanktiolaji, toimenpidekoodi)
VALUES
  ('Muu tuote', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'muistutus'::sanktiolaji], NULL),
  ('Talvihoito', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'muistutus'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23104')),
  ('Laatuasiakirjojen, seurantaraprottien yms. vastavien tietojen paikkansa pitämättömyyt', ARRAY['C'::sanktiolaji], NULL);
>>>>>>> develop
