-- name: luo-csrf-sessio-kayttajanimelle<!
INSERT INTO kayttaja_anti_csrf_token (kayttaja_id, anti_csrf_token, voimassa, luotu)
VALUES ((SELECT id
         FROM kayttaja
         WHERE kayttajanimi = :kayttajanimi), :csrf_token, :voimassa, :nyt);

-- name: virkista-kayttajanimen-csrf-sessio-jos-voimassa<!
UPDATE kayttaja_anti_csrf_token
SET voimassa = :voimassa
WHERE kayttaja_id = (SELECT id
                     FROM kayttaja
                     WHERE kayttajanimi = :kayttajanimi)
      AND anti_csrf_token = :csrf_token
      AND voimassa >= :nyt;

-- name: poista-kayttajanimen-vanhentuneet-csrf-sessiot!
DELETE FROM kayttaja_anti_csrf_token
WHERE kayttaja_id = (SELECT id
                     FROM kayttaja
                     WHERE kayttajanimi = :kayttajanimi)
      AND voimassa < :nyt;

-- name: hae-kayttajan-voimassaoleva-csrf-token
SELECT id
FROM kayttaja_anti_csrf_token
WHERE kayttaja_id = (SELECT id
                     FROM kayttaja
                     WHERE kayttajanimi = :kayttajanimi)
      AND anti_csrf_token = :csrf_token
      AND voimassa >= :nyt
      LIMIT 1;
