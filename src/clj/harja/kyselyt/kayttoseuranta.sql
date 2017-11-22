-- name: kirjaa-kaytto<!
INSERT INTO kayttoseuranta
(kayttaja, aika, tila, sivu, lisatieto)
VALUES
  (:kayttaja, NOW(), :tila, :sivu, :lisatieto);
