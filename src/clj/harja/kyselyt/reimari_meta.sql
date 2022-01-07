-- name: paivita-aikakursori!
UPDATE reimari_meta
  SET aikakursori = :aika
WHERE integraatio = (select id from integraatio where jarjestelma = 'reimari' and nimi = :integraatio);
