-- name: kommentoi-toimenpiteen-hintaryhmaa<!
INSERT INTO vv_hinnoittelun_kommentti
(tila, aika, kommentti, "kayttaja-id", "hinnoittelu-id")
  VALUES
    (:tila,
     :aika,
     :kommentti,
     :kayttaja_id,
     (SELECT hintaryhman_id FROM vv_toimenpiteen_hinnoittelun_hintaryhma WHERE oman_hinnan_id = :toimenpide_id));
