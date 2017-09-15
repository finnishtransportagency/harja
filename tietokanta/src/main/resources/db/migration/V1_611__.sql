-- Harjaan on syntynyt 14 ilmoitusta (HAR-6113) jotka ovat ilmoitusid-sarakkeen osalta duplikaatteja
-- Tarkkaa syytä miksi näin ei vielä tiedetä, mutta estetään tämän syntyminen tulevaisuudessa
-- poistamalla duplikaatit ennen unique constraintin luontia (jätetään pienimmällä id:llä oleva)
CREATE OR REPLACE FUNCTION muuta_duplikaatti_ilmoitusidt() RETURNS VOID AS $$
DECLARE
  ilmoitusid_sekvenssin_alku INTEGER;
  i               RECORD;
  jarjestysluku INTEGER := 0 ;
BEGIN
  -- Käsin poistetut duplikaatit sijoitetaan ilmoitusid-avaruuteen 4000 0000-4999 9999
  ilmoitusid_sekvenssin_alku := 40000000;

  FOR i IN SELECT dups.id
           FROM (SELECT id, ilmoitusid, ROW_NUMBER() OVER (partition BY ilmoitusid
             ORDER BY id) AS row
                 FROM ilmoitus ilm WHERE ilm.ilmoitusid IS NOT NULL) dups
           WHERE dups.row > 1
  LOOP
    RAISE NOTICE 'Päivitetään ilmoitus id:lle % uusi ilmoitusid: %', i.id, ilmoitusid_sekvenssin_alku + jarjestysluku;
    UPDATE ilmoitus SET ilmoitusid = ilmoitusid_sekvenssin_alku + jarjestysluku WHERE id = i.id;
    RAISE NOTICE 'Päivitetään ilmoitustoimenpiteen ilmoitusid-linkki';
    UPDATE ilmoitustoimenpide SET ilmoitusid = ilmoitusid_sekvenssin_alku + jarjestysluku WHERE ilmoitus = i.id;

    jarjestysluku := jarjestysluku +1;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT * FROM muuta_duplikaatti_ilmoitusidt();

CREATE UNIQUE INDEX uniikki_ilmoitus_ilmoitusid on ilmoitus(ilmoitusid) WHERE ilmoitusid IS NOT NULL;

DROP FUNCTION IF EXISTS muuta_duplikaatti_ilmoitusidt();