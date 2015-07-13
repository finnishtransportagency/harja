-- name: luo-integraatiotapahtuma<!
-- Luo uuden hankkeen.
INSERT INTO integraatiotapahtuma (integraatio, alkanut, ulkoinenid)
VALUES ((SELECT id
         FROM integraatio
         WHERE jarjestelma = :jarjestelma AND nimi = :nimi),
        current_timestamp,
        :ulkoinenid);

-- name: merkitse-integraatiotapahtuma-paattyneeksi!
-- Paivittaa hankkeen Samposta saaduilla tiedoilla
UPDATE integraatiotapahtuma
SET paattynyt = current_timestamp, onnistunut = :onnistunut, lisatietoja = :lisatietoja
WHERE id = :id;

-- name: merkitse-integraatiotapahtuma-paattyneeksi-ulkoisella-idlla<!
-- Paivittaa hankkeen Samposta saaduilla tiedoilla
UPDATE integraatiotapahtuma
SET paattynyt = current_timestamp, onnistunut = :onnistunut, lisatietoja = :lisatietoja
WHERE ulkoinenid = :ulkoinen_id;

-- name: luo-integraatioviesti<!
-- Luo uuden integraatioviestin
INSERT INTO integraatioviesti (integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit)
VALUES
  (:integraatiotapahtuma, :suunta :: integraatiosuunta, :sisaltotyyppi, :siirtotyyppi, :sisalto, :otsikko, :parametrit);

-- name: hae-tapahtumaid-ulkoisella-idlla
-- Hakee tapahtuma id:n ulkoisella id:llä ja integraatiolla
SELECT id
FROM integraatiotapahtuma
WHERE ulkoinenid = :ulkoinenid AND
      integraatio = (SELECT id
                     FROM integraatio
                     WHERE jarjestelma = :jarjestelma AND nimi = :nimi);


