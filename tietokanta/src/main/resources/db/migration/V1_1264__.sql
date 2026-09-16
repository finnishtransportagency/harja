CREATE TABLE bonus_laji (
    id            SERIAL PRIMARY KEY,
    koodi         TEXT                     NOT NULL UNIQUE,
    nimi          TEXT                     NOT NULL,
    kuvaus        TEXT,
    kirjaustapa   TEXT                     NOT NULL,
    automaattinen BOOLEAN                  NOT NULL DEFAULT FALSE,
    aktiivinen    BOOLEAN                  NOT NULL DEFAULT TRUE,
    jarjestys     INTEGER                  NOT NULL,
    luoja         INTEGER                  NOT NULL REFERENCES kayttaja (id),
    luotu         TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja     INTEGER                  NOT NULL REFERENCES kayttaja (id),
    muokattu      TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (kirjaustapa IN ('sanktiot-ja-bonukset', 'valikatselmus'))
);

CREATE TABLE bonus_profiili (
    id                 SERIAL PRIMARY KEY,
    nimi               TEXT              NOT NULL UNIQUE,
    urakkatyyppi       TEXT              NOT NULL,
    hoitovuosi_alku    INTEGER           NOT NULL,
    hoitovuosi_loppu   INTEGER           NOT NULL,
    alkupvm            DATE              NOT NULL,
    loppupvm           DATE,
    aktiivinen         BOOLEAN           NOT NULL DEFAULT TRUE,
    luoja              INTEGER           NOT NULL REFERENCES kayttaja (id),
    luotu              TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja          INTEGER           NOT NULL REFERENCES kayttaja (id),
    muokattu           TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (hoitovuosi_alku <= hoitovuosi_loppu),
    CHECK (loppupvm IS NULL OR alkupvm <= loppupvm)
);

CREATE TABLE bonus_profiili_laji_esitystiedot (
    id                SERIAL PRIMARY KEY,
    bonus_profiili_id INTEGER                  NOT NULL REFERENCES bonus_profiili (id),
    bonus_laji_id     INTEGER                  NOT NULL REFERENCES bonus_laji (id),
    nimi              TEXT,
    kuvaus            TEXT,
    luoja             INTEGER                  NOT NULL REFERENCES kayttaja (id),
    luotu             TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja         INTEGER                  NOT NULL REFERENCES kayttaja (id),
    muokattu          TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bonus_profiili_id, bonus_laji_id),
    CHECK (nimi IS NOT NULL OR kuvaus IS NOT NULL),
    CHECK (nimi IS NULL OR btrim(nimi) <> '')
);

CREATE TABLE bonus_profiili_rivi (
    id                 SERIAL PRIMARY KEY,
    bonus_profiili_id  INTEGER          NOT NULL REFERENCES bonus_profiili (id),
    bonus_laji_id      INTEGER          NOT NULL REFERENCES bonus_laji (id),
    toimenpideinstanssi_rajauksen_tyyppi
                       TEXT             NOT NULL DEFAULT 'kaikki',
    toimenpideinstanssi_t2_koodi
                       TEXT,
    jarjestys          INTEGER          NOT NULL,
    aktiivinen         BOOLEAN          NOT NULL DEFAULT TRUE,
    luoja              INTEGER          NOT NULL REFERENCES kayttaja (id),
    luotu              TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja          INTEGER          NOT NULL REFERENCES kayttaja (id),
    muokattu           TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (toimenpideinstanssi_rajauksen_tyyppi IN ('kaikki', 't2-koodi')),
    CHECK (
        (toimenpideinstanssi_rajauksen_tyyppi = 'kaikki' AND toimenpideinstanssi_t2_koodi IS NULL)
        OR
        (toimenpideinstanssi_rajauksen_tyyppi = 't2-koodi' AND toimenpideinstanssi_t2_koodi IS NOT NULL AND btrim(toimenpideinstanssi_t2_koodi) <> '')
    )
);

CREATE TABLE bonus_profiili_rivi_urakka (
    id                     SERIAL PRIMARY KEY,
    bonus_profiili_rivi_id INTEGER          NOT NULL REFERENCES bonus_profiili_rivi (id),
    urakka_id              INTEGER          NOT NULL REFERENCES urakka (id),
    luoja                  INTEGER          NOT NULL REFERENCES kayttaja (id),
    luotu                  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokkaaja              INTEGER          NOT NULL REFERENCES kayttaja (id),
    muokattu               TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bonus_profiili_rivi_id, urakka_id)
);

CREATE INDEX bonus_profiili_haku_idx
    ON bonus_profiili (urakkatyyppi, aktiivinen, alkupvm, loppupvm, hoitovuosi_alku, hoitovuosi_loppu);

CREATE INDEX bonus_profiili_laji_esitystiedot_haku_idx
    ON bonus_profiili_laji_esitystiedot (bonus_profiili_id, bonus_laji_id);

CREATE UNIQUE INDEX bonus_profiili_rivi_kaikki_unique_idx
    ON bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id)
    WHERE toimenpideinstanssi_rajauksen_tyyppi = 'kaikki';

CREATE UNIQUE INDEX bonus_profiili_rivi_t2_unique_idx
    ON bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id, toimenpideinstanssi_t2_koodi)
    WHERE toimenpideinstanssi_rajauksen_tyyppi = 't2-koodi';

CREATE INDEX bonus_profiili_rivi_haku_idx
    ON bonus_profiili_rivi (bonus_profiili_id, toimenpideinstanssi_rajauksen_tyyppi, toimenpideinstanssi_t2_koodi, aktiivinen, jarjestys);

CREATE INDEX bonus_profiili_rivi_urakka_haku_idx
    ON bonus_profiili_rivi_urakka (bonus_profiili_rivi_id, urakka_id);

COMMENT ON TABLE bonus_laji
                IS 'Bonusten lajimasterdata parametrisoitua bonus-konfiguraatiota varten.';

COMMENT ON TABLE bonus_profiili
                IS 'Urakka- ja hoitovuosikontekstissa resolvoitava bonusten konfiguraatioprofiili.';

COMMENT ON TABLE bonus_profiili_laji_esitystiedot
                IS 'Bonuslajin profiilikohtaiset esitystiedot, kuten nimi ja kuvaus.';

COMMENT ON TABLE bonus_profiili_rivi
                IS 'Sallitut bonus_laji-rivit profiileittain joko kaikkiin toimenpideinstansseihin tai tiettyyn t2-koodiin rajattuina.';

COMMENT ON TABLE bonus_profiili_rivi_urakka
                IS 'Bonus-profiilirivin urakkakohtainen whitelist-rajaus.';

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO bonus_laji (koodi, nimi, kuvaus, kirjaustapa, automaattinen, aktiivinen, jarjestys, luoja, luotu, muokkaaja, muokattu)
VALUES ('asiakastyytyvaisyysbonus', 'Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta', 'Manuaalinen asiakastyytyvaisyysbonus, jonka perusnimea voidaan tarvittaessa taydentaa profiilikohtaisilla esitystiedoilla', 'sanktiot-ja-bonukset', FALSE, TRUE, 1, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('alihankintabonus', 'Alihankintasopimusten maksuehtobonus', 'Manuaalinen alihankintabonus ennen MHU25-kautta', 'sanktiot-ja-bonukset', FALSE, TRUE, 2, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('alihankkijatyytyvaisyyskyselybonus', 'Bonus alihankkijatyytyväisyyden kyselytutkimuksen tuloksesta', 'MHU26-kauden bonus, joka siirrettiin lupauksista bonuksiin', 'sanktiot-ja-bonukset', FALSE, TRUE, 3, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('maaraaikaan_tehtavien_toiden_aiempi_toteutusbonus', 'Bonus määräaikaan tehtävien töiden aiemmasta toteutuksesta', 'Uusi MHU26-kauden bonus', 'sanktiot-ja-bonukset', FALSE, TRUE, 4, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('liikennevahinkojen_aiheuttajien_selvitysbonus', 'Bonus liikennevahinkojen aiheuttajien selvittämisestä', 'Urakkakohtainen MHU26-bonus Nummi- ja Raasepori-urakoille', 'sanktiot-ja-bonukset', FALSE, TRUE, 5, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('muu-bonus', 'Muu bonus', 'Legacy- ja poikkeusluonteinen bonuskori', 'sanktiot-ja-bonukset', FALSE, TRUE, 6, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('lupausbonus', 'Lupausbonus', 'Valikatselmuksen automaattinen bonus', 'valikatselmus', TRUE, TRUE, 7, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP);

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO bonus_profiili (nimi, urakkatyyppi, hoitovuosi_alku, hoitovuosi_loppu, alkupvm, loppupvm, aktiivinen, luoja, luotu, muokkaaja, muokattu)
VALUES ('hoito-bonus-legacy', 'hoito', 1, 20, DATE '1900-01-01', DATE '2021-09-30', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-bonus-legacy', 'teiden-hoito', 1, 20, DATE '1900-01-01', DATE '2021-09-30', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('hoito-bonus-2021-ja-uudemmat', 'hoito', 1, 20, DATE '2021-10-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-bonus-2021-2024', 'teiden-hoito', 1, 20, DATE '2021-10-01', DATE '2025-09-30', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-bonus-mhu2025', 'teiden-hoito', 1, 20, DATE '2025-10-01', DATE '2026-09-30', TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP),
       ('teiden-hoito-bonus-mhu2026', 'teiden-hoito', 1, 20, DATE '2026-10-01', NULL, TRUE, (SELECT id FROM integraatio), CURRENT_TIMESTAMP, (SELECT id FROM integraatio), CURRENT_TIMESTAMP);

-- Teiden-hoito -urakoissa bonuslogiikka riippuu nykyisin toimenpideinstanssin t2-koodista.
-- Rajauksen tyyppi kertoo, koskeeko rivi kaikkia profiilin toimenpideinstansseja vai vain tiettya t2-koodia.
WITH profiilirivit (profiili_nimi, bonus_koodi, toimenpideinstanssi_rajauksen_tyyppi, toimenpideinstanssi_t2_koodi, jarjestys) AS (
    VALUES
                                ('hoito-bonus-legacy', 'asiakastyytyvaisyysbonus', 'kaikki', NULL, 1),
                                ('hoito-bonus-legacy', 'muu-bonus', 'kaikki', NULL, 2),

                                ('hoito-bonus-2021-ja-uudemmat', 'asiakastyytyvaisyysbonus', 'kaikki', NULL, 1),
                                ('hoito-bonus-2021-ja-uudemmat', 'muu-bonus', 'kaikki', NULL, 2),

                                ('teiden-hoito-bonus-legacy', 'asiakastyytyvaisyysbonus', 't2-koodi', '23150', 1),
                                ('teiden-hoito-bonus-legacy', 'alihankintabonus', 't2-koodi', '23150', 2),
                                ('teiden-hoito-bonus-legacy', 'muu-bonus', 'kaikki', NULL, 3),

                                ('teiden-hoito-bonus-2021-2024', 'asiakastyytyvaisyysbonus', 't2-koodi', '23150', 1),
                                ('teiden-hoito-bonus-2021-2024', 'alihankintabonus', 't2-koodi', '23150', 2),

                                ('teiden-hoito-bonus-mhu2025', 'asiakastyytyvaisyysbonus', 't2-koodi', '23150', 1),

                                ('teiden-hoito-bonus-mhu2026', 'asiakastyytyvaisyysbonus', 't2-koodi', '23150', 1),
                                ('teiden-hoito-bonus-mhu2026', 'alihankkijatyytyvaisyyskyselybonus', 't2-koodi', '23150', 2),
                                ('teiden-hoito-bonus-mhu2026', 'maaraaikaan_tehtavien_toiden_aiempi_toteutusbonus', 't2-koodi', '23150', 3),
                                ('teiden-hoito-bonus-mhu2026', 'liikennevahinkojen_aiheuttajien_selvitysbonus', 't2-koodi', '23150', 4)
),
integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id, toimenpideinstanssi_rajauksen_tyyppi, toimenpideinstanssi_t2_koodi, jarjestys, aktiivinen, luoja, luotu, muokkaaja, muokattu)
SELECT bp.id,
       bl.id,
             pr.toimenpideinstanssi_rajauksen_tyyppi,
       pr.toimenpideinstanssi_t2_koodi,
       pr.jarjestys,
       TRUE,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP
FROM profiilirivit pr
       JOIN bonus_profiili bp
         ON bp.nimi = pr.profiili_nimi
       JOIN bonus_laji bl
         ON bl.koodi = pr.bonus_koodi;

WITH urakkarajaukset (profiili_nimi, bonus_koodi, toimenpideinstanssi_rajauksen_tyyppi, toimenpideinstanssi_t2_koodi, urakka_lyhyt_nimi) AS (
    VALUES
                ('teiden-hoito-bonus-mhu2026', 'liikennevahinkojen_aiheuttajien_selvitysbonus', 't2-koodi', '23150', 'Nummi 26'),
                ('teiden-hoito-bonus-mhu2026', 'liikennevahinkojen_aiheuttajien_selvitysbonus', 't2-koodi', '23150', 'Raasepori 26')
),
integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO bonus_profiili_rivi_urakka (bonus_profiili_rivi_id, urakka_id, luoja, luotu, muokkaaja, muokattu)
SELECT bpr.id,
       u.id,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP,
       (SELECT id FROM integraatio),
       CURRENT_TIMESTAMP
FROM urakkarajaukset ur
             JOIN bonus_profiili bp
                 ON bp.nimi = ur.profiili_nimi
             JOIN bonus_laji bl
                 ON bl.koodi = ur.bonus_koodi
             JOIN bonus_profiili_rivi bpr
                 ON bpr.bonus_profiili_id = bp.id
                AND bpr.bonus_laji_id = bl.id
                AND bpr.toimenpideinstanssi_rajauksen_tyyppi = ur.toimenpideinstanssi_rajauksen_tyyppi
                AND bpr.toimenpideinstanssi_t2_koodi = ur.toimenpideinstanssi_t2_koodi
             JOIN urakka u
                 ON u.lyhyt_nimi = ur.urakka_lyhyt_nimi
                AND u.tyyppi = 'teiden-hoito'
                AND u.alkupvm = DATE '2026-10-01';
