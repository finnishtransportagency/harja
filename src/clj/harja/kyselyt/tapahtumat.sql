-- name: tapahtuman-kanava
-- single?: true
SELECT kanava FROM tapahtuma WHERE nimi = :nimi;

-- name: lisaa-tapahtuma
-- single?: true
INSERT INTO tapahtuma (nimi, kanava)
  VALUES (:nimi, :kanava)
ON CONFLICT (nimi) DO NOTHING
RETURNING kanava;

-- name: julkaise-tapahtuma
-- single?: true
SELECT julkaise_tapahtuma(:kanava::TEXT, :data::JSONB, :data::TEXT, :hash::TEXT);

-- name: uusin-arvo
SELECT arvo, hash, luotu
FROM tapahtuman_tiedot
WHERE id=(SELECT uusin_arvo
          FROM tapahtuma
          WHERE nimi = :nimi);

-- name: uusin-arvo-per-palvelin
WITH palvelimet_viimeisine_arvoineen AS (SELECT key AS palvelin,
                                                value::INT AS id
                                         FROM jsonb_each_text((SELECT palvelimien_uusimmat_arvot
                                                               FROM tapahtuma
                                                               WHERE nimi = :nimi)))
SELECT tt.arvo, tt.hash, tt.luotu, pva.palvelin
FROM tapahtuman_tiedot tt
  JOIN palvelimet_viimeisine_arvoineen pva ON pva.id=tt.id;

-- name: tapahtuman-tiedot
SELECT arvo, hash, luotu
FROM tapahtuman_tiedot
WHERE id IN (:idt);

-- name: kaikki-kanavat
SELECT kanava FROM tapahtuma;