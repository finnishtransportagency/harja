CREATE VIEW vv_hyvaksytyt_hinnoittelut
  AS
    SELECT
      "hinnoittelu-id" AS id,
      "laskutus-pvm"
    FROM
      (SELECT DISTINCT ON ("hinnoittelu-id")
         "hinnoittelu-id",
         "laskutus-pvm",
         tila
       FROM vv_hinnoittelu_kommentti
       ORDER BY "hinnoittelu-id", aika DESC) AS tilat
      JOIN vv_hinnoittelu h ON tilat."hinnoittelu-id" = h.id AND
                               h.poistettu IS NOT TRUE
    WHERE tila = 'hyvaksytty';

CREATE VIEW vv_toimenpiteen_hinnoittelut
  AS
    SELECT
      rt.id          AS toimenpiteen_id,
      hintaryhmat.id AS hintaryhman_id,
      hinnat.id      AS oman_hinnoittelun_id
    FROM reimari_toimenpide rt
      JOIN vv_hinnoittelu_toimenpide v
        ON rt.id = v."toimenpide-id" AND v.poistettu IS NOT TRUE AND rt.poistettu IS NOT TRUE
      LEFT JOIN vv_hinnoittelu hintaryhmat
        ON v."hinnoittelu-id" = hintaryhmat.id AND hintaryhmat.hintaryhma IS TRUE AND
           hintaryhmat.poistettu IS NOT TRUE
      LEFT JOIN vv_hinnoittelu hinnat
        ON v."hinnoittelu-id" = hinnat.id AND hinnat.hintaryhma IS NOT TRUE AND
           hinnat.poistettu IS NOT TRUE;