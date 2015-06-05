-- name: luo-kommentti<!
-- Luo uuden kommentin
INSERT
  INTO kommentti
       (tekija, kommentti, liite, luoja, luotu)
VALUES (:tekija::osapuoli, :kommentti, :liite, :luoja, current_timestamp)
