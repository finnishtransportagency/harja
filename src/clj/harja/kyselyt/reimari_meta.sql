-- name: hae-hakuvali
SELECT (aikakursori - '4 days'::interval) as alku, LEAST(aikakursori + enimmaishakuvali, now()) as loppu
  FROM reimari_meta m, integraatio i
  WHERE m.integraatio = i.id AND i.jarjestelma = 'reimari' AND i.nimi = :integraatio
  LIMIT 1;

-- name: paivita-aikakursori!
UPDATE reimari_meta
  SET aikakursori = :aika
WHERE integraatio = (select id from integraatio where jarjestelma = 'reimari' and nimi = :integraatio);
