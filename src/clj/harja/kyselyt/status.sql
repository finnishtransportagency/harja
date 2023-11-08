-- name: hae-replikoinnin-viive
-- single?: true
-- Palauttaa replikoinnin viiveen sekunteina.
-- HUOM: tämä ei ole pomminvarma tapa. Jos master ei ole aktiivinen
-- tämä luku ei tarkoita mitään.
SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))::INT;

-- name: db-on-aurora
-- single?: true
-- Palauttaa true tai false, mikäli
SELECT aurora_version() IS NOT NULL;

-- name: hae-replikoinnin-viive-aurora
-- single?: true
-- Palauttaa replikoinnin viiveen sekunteina.
-- HUOM: Replica-status palauttaa useita rivejä, varsinkin jos readereita on useampi.
--       Tässä haetaan not-null arvo viiveelle ja rajoitetaan tulokset yhteen.
--       Tapauksessa, jossa halutaan tietyn readerin replica_lag, täytyy kertoa kyselylle minkä readerin status halutaan.
--       Tällä hetkellä käytössä on vain yksi master ja yksi reader, joten valintaa ei tarvi tehdä.
SELECT FLOOR((SELECT replica_lag_in_msec
              FROM aurora_replica_status()
              WHERE replica_lag_in_msec IS NOT NULL
              LIMIT 1) / 1000)::INT;


-- name: tarkista-kantayhteys
-- single?: true
SELECT 1 + 2;

-- name: hae-komponenttien-tila
SELECT distinct on (concat(ks.palvelin,'-', ks.komponentti)) ks.komponentti AS komponentti,
                                                             ks.palvelin    AS palvelin,
                                                             ks.status      AS status,
                                                             ks.luotu       AS luotu,
                                                             ks.lisatiedot  as lisatiedot
FROM komponenttien_status ks
order by concat(ks.palvelin,'-', ks.komponentti), ks. luotu DESC;

-- name: aseta-komponentin-tila<!
INSERT INTO komponenttien_status (palvelin, komponentti, status, lisatiedot, luotu)
VALUES
    (:palvelin, :komponentti, :status::komponentti_status_tyyppi, :lisatiedot, NOW());

-- name: hae-tietokannan-tila
-- single?: true
select 1 from toteuma limit 1;

-- name: poista-statusviestit!
DELETE
  FROM komponenttien_status ks
 WHERE ks.luotu < NOW() - INTERVAL '2 days';
