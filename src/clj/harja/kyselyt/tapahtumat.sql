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
SELECT julkaise_tapahtuma(:kanava::TEXT, :data::JSONB);

-- name: uusin-arvo
-- single?: true
SELECT uusin_arvo::TEXT
FROM tapahtuma
WHERE nimi = :nimi;

-- name: uusin-arvo-per-palvelin
SELECT key AS palvelin,
       value AS data
FROM jsonb_each_text((SELECT palvelimien_uusimmat_arvot
                      FROM tapahtuma
                      WHERE nimi = :nimi));

-- name: tapahtuman-arvot
SELECT arvo::TEXT
FROM tapahtuman_tiedot
WHERE id IN (:idt);

-- name: kaikki-kanavat
SELECT kanava FROM tapahtuma;