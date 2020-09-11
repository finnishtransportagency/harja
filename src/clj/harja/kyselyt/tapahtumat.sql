-- name: tapahtuman-tunnistin
-- single?: true
SELECT kanava FROM tapahtuma WHERE nimi = :nimi;

-- name: lisaa-tapahtuma
INSERT INTO tapahtuma (nimi, kanava)
  VALUES (:nimi, :kanava)
RETURNING kanava ON CONFLICT DO NOTHING;

-- name: julkaise-tapahtuma
SELECT julkaise_tapahtuma(:kanava::TEXT, :data::JSONB);

-- name: uusin-arvo
SELECT uusin_arvo FROM tapahtuma WHERE nimi = :nimi