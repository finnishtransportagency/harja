-- name: kuittaa-integraatiotapahtuma!
-- Kuittaa integraatiotapahtuman ulkoisella id:lla
UPDATE integraatiotapahtuma
   SET paattynyt = current_timestamp,
       onnistunut = :onnistunut,
       lisatietoja = :lisatietoja
 WHERE integraatio = :integraatio AND ulkoinenid = :ulkoinenid;

-- name: hae-integraatiotapahtuman-tila
SELECT alkanut, paattynyt, onnistunut
  FROM integraatiotapahtuma
 WHERE integraatio = :integraatio AND ulkoinenid = :ulkoinenid;


-- name: hae-integraation-id
-- Hakee integraation id:n järjestelmän ja nimen perusteella
SELECT id FROM integraatio WHERE jarjestelma = :jarjestelma AND nimi = :nimi;
