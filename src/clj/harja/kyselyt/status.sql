-- name: hae-viimeisin-toteuman-luontiaika
-- single?: true
-- Hakee viimeisimmän toteuman luontiajan
SELECT luotu FROM toteuma ORDER BY id DESC LIMIT 1;

-- name: hae-replikoinnin-viive
-- single?: true
-- Palauttaa replikoinnin viiveen sekunteina.
-- HUOM: tämä ei ole pomminvarma tapa. Jos master ei ole aktiivinen
-- tämä luku ei tarkoita mitään.
SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))::INT;


-- name: tarkista-kantayhteys
-- single?: true
SELECT 1 + 2;
