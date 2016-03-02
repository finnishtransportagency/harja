<<<<<<< HEAD
CREATE TABLE asiakaspalauteluokka (
  id        SERIAL PRIMARY KEY,
  nimi      VARCHAR(128),
  selitteet ilmoituksenselite []
);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Auraus ja sohjonpoisto',
        ARRAY ['auraustarve',
        'sohjonPoisto',
        'kevyenLiikenteenVaylillaOnLunta']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Liukkaudentorjunta',
        ARRAY ['hiekoitustarve',
        'kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita',
        'kevyenLiikenteenVaylatOvatLiukkaita',
        'liukkaudentorjuntatarve',
        'raskasAjoneuvoJumissa',
        'tieOnLiukas']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Polanteen tasaus',
        ARRAY ['hoylaystarve']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Muu talvihoito',
        ARRAY ['aurausvallitNakemaesteena']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito',
        ARRAY ['avattavatPuomit',
        'liikennemerkkeihinLiittyvaIlmoitus',
        'liikennevalotEivatToimi',
        'muuttuvatOpasteetEivatToimi',
        'tienvarsilaitteisiinLiittyvaIlmoitus']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Puhtaanapito ja kalusteiden hoito',
        ARRAY ['avattavatPuomit',
        Harjaustarve
        Irtokiviä tiellä
        Lasia tiellä
        Levähdysalueeseen liittyvä ilmoitus
        Liikennettä vaarantava este tiellä
        Pysäkkiin liittyvä ilmoitus
        Savea tiellä]
        :: ilmoituksenselite []);
=======
-- Pudota suljettu-boolean ilmoitukselta, vanhentunut
ALTER TABLE ilmoitus DROP COLUMN suljettu;

CREATE OR REPLACE FUNCTION hae_seuraava_vapaa_viestinumero(yhteyshenkilo_id INTEGER)
  RETURNS INTEGER AS $$
BEGIN
  LOCK TABLE paivystajatekstiviesti IN ACCESS EXCLUSIVE MODE;
  RETURN (SELECT coalesce((SELECT (SELECT max(p.viestinumero)
                                   FROM paivystajatekstiviesti p
                                     INNER JOIN ilmoitus i ON p.ilmoitus = i.id
                                   WHERE p.yhteyshenkilo = 1 AND
                                         NOT exists(SELECT itp.id
                                                    FROM ilmoitustoimenpide itp
                                                    WHERE
                                                      itp.ilmoitus = i.id AND
                                                      itp.kuittaustyyppi = 'lopetus'))), 0)
                 + 1 AS viestinumero);
END;
$$ LANGUAGE plpgsql;
>>>>>>> develop
