-- name: hae-replikoinnin-viive
-- single?: true
-- Palauttaa replikoinnin viiveen sekunteina.
-- HUOM: tämä ei ole pomminvarma tapa. Jos master ei ole aktiivinen
-- tämä luku ei tarkoita mitään.
SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))::INT;


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
