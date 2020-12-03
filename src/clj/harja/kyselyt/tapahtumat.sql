-- name: hae-tapahtuman-kanava
-- single?: true
SELECT kanava FROM tapahtumatyyppi WHERE nimi = :nimi;

-- name: lisaa-tapahtuma
-- single?: true
INSERT INTO tapahtumatyyppi (nimi, kanava)
  VALUES (:nimi, :kanava::TEXT)
ON CONFLICT DO NOTHING
RETURNING kanava;

-- name: julkaise-tapahtuma
-- single?: true
SELECT julkaise_tapahtuma(:kanava::TEXT, :data::TEXT, :hash::TEXT, :palvelin::TEXT);

-- name: hae-uusin-arvo
WITH nimen_kanava AS (SELECT kanava FROM tapahtumatyyppi WHERE nimi = :nimi),
     uusin_aika AS (SELECT max(luotu) FROM tapahtuman_tiedot WHERE kanava = nimen_kanava)
SELECT arvo, hash, luotu, palvelin
FROM tapahtuman_tiedot
WHERE luotu = uusin_aika AND
      kanava = nimen_kanava;

-- name: hae-uusin-arvo-per-palvelin
WITH nimen_kanava AS (SELECT kanava FROM tapahtumatyyppi WHERE nimi = :nimi),
     palvelimet_viimeisine_aikoineen (SELECT max(luotu) AS luotu,
                                             palvelin
                                      FROM tapahtuman_tiedot
                                      WHERE kanava=nimen_kanava
                                      GROUP BY palvelin)
SELECT tt.arvo, tt.hash, tt.luotu, tt.palvelin
FROM tapahtuman_tiedot tt
  JOIN palvelimet_viimeisine_aikoineen pva ON pva.palvelin=tt.palvelin AND
                                              pva.luotu=tt.luotu;

-- name: hae-tapahtuman-tiedot
SELECT arvo, hash, luotu, palvelin
FROM tapahtuman_tiedot
WHERE id IN (:idt);

-- name: hae-kaikki-kanavat
SELECT kanava FROM tapahtumatyyppi;