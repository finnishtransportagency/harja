-- name: hae-koulutusvideot
-- Hakee kaikki järjestelmän koulutusvideot
SELECT
  id,
  otsikko,
  linkki,
  pvm
FROM koulutusvideot;