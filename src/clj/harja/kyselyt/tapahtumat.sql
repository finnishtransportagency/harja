-- name: tapahtuman-kanava
-- single?: true
SELECT kanava FROM tapahtuma WHERE nimi = :nimi;

-- name: lisaa-tapahtuma
INSERT INTO tapahtuma (nimi, kanava)
  VALUES (:nimi, :kanava)
ON CONFLICT (nimi) DO NOTHING
RETURNING kanava;

-- name: julkaise-tapahtuma
-- single?: true
SELECT julkaise_tapahtuma(:kanava::TEXT, :data::JSONB);

-- name: uusin-arvo
-- single?: true
SELECT uusin_arvo FROM tapahtuma WHERE nimi = :nimi