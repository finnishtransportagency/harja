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
     uusin_aika AS (SELECT max(luotu) AS luotu,
                           tt.kanava
                    FROM tapahtuman_tiedot tt
                       JOIN nimen_kanava nk ON nk.kanava=tt.kanava
                    GROUP BY tt.kanava)
SELECT tt.arvo, tt.hash, tt.luotu, tt.palvelin, tt.id
FROM tapahtuman_tiedot tt
  JOIN uusin_aika ua ON ua.luotu = tt.luotu AND
                        ua.kanava = tt.kanava
LIMIT 1;

-- name: hae-uusin-arvo-per-palvelin
WITH nimen_kanava AS (SELECT kanava FROM tapahtumatyyppi WHERE nimi = :nimi),
     palvelimet_viimeisine_aikoineen AS (SELECT max(luotu) AS luotu,
                                                tt.palvelin,
                                                tt.kanava
                                         FROM tapahtuman_tiedot tt
                                           JOIN nimen_kanava nk ON nk.kanava=tt.kanava
                                         GROUP BY tt.palvelin, tt.kanava),
     palvelimien_viimeisimmat_arvot AS (SELECT max(tt.id) AS id
                                        FROM tapahtuman_tiedot tt
                                          JOIN palvelimet_viimeisine_aikoineen pva ON pva.palvelin=tt.palvelin AND
                                                                                      pva.luotu=tt.luotu AND
                                                                                      pva.kanava=tt.kanava
                                        GROUP BY tt.palvelin)
SELECT tt.arvo, tt.hash, tt.luotu, tt.palvelin, tt.id
FROM tapahtuman_tiedot tt
  JOIN palvelimien_viimeisimmat_arvot pva ON tt.id=pva.id;

-- name: hae-tapahtuman-tiedot
SELECT arvo, hash, luotu, palvelin
FROM tapahtuman_tiedot
WHERE id IN (:idt);

-- name: hae-kaikki-kanavat
SELECT kanava FROM tapahtumatyyppi;