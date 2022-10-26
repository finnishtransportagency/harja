-- name: aseta-tieluvalle-urakka
SELECT aseta_tieluvalle_urakka(:id);

-- name: hae-id-ulkoisella-tunnisteella
SELECT id
FROM tielupa
WHERE "ulkoinen-tunniste" = :ulkoinen_tunniste;

-- name: liita-liite-tieluvalle<!
INSERT INTO tielupa_liite (tielupa, liite)
VALUES (:tielupa, :liite);

-- name: hae-tielupien-liitteet
SELECT
  *
FROM liite l JOIN tielupa_liite t ON l.id = t.liite
WHERE t.tielupa IN (:tielupa);

-- name: hae-urakan-tieluvat
SELECT *
FROM tielupa
WHERE urakat @> ARRAY[:urakkaid] ::INT[];

-- name: hae-tienpidon-luvat
-- Haetaan monimutkaisten hakuehtojen mukaan tieluvat kannasta
SELECT id, myontamispvm, "voimassaolon-alkupvm",
       "voimassaolon-loppupvm", tyyppi,
       "hakija-nimi", "hakija-tyyppi", "hakija-puhelinnumero", "hakija-sahkopostiosoite",
       "paatoksen-diaarinumero",
       jsonb_agg(row_to_json(row(s.tie, s.aosa, s.aet, s.losa, s.let, ST_asText(s.geometria)))) as sijainnit,
    jsonb_agg(row_to_json(row(l.liikennemerkki, ST_asText(l.geometria)))) as liikennemerkkijarjestelyt
 FROM tielupa tl
      LEFT JOIN  LATERAL unnest(tl.sijainnit) s ON TRUE
      LEFT JOIN LATERAL  unnest(tl.liikennemerkkijarjestelyt) l ON TRUE
WHERE (:hakija-nimi::TEXT IS NULL OR upper(tl."hakija-nimi") ilike upper(:hakija-nimi))
  AND (:tyyppi::tielupatyyppi IS NULL OR tl.tyyppi = :tyyppi::tielupatyyppi)
  AND (:paatoksen-diaarinumero::TEXT IS NULL OR tl."paatoksen-diaarinumero" = :paatoksen-diaarinumero)
  -- Haetaan joko annetun alkupvm:n jälkeen alkaneet luvat
  -- Tai haetaan luvat, joiden voimassaolo on menossa annetulla aikavälillä = overlap
  AND (:voimassaolon-alkupvm::DATE IS NULL
           OR (:voimassaolon-loppupvm::DATE IS NULL AND tl."voimassaolon-alkupvm" >= :voimassaolon-alkupvm::DATE)
           OR (:voimassaolon-loppupvm::DATE IS NOT NULL AND tl."voimassaolon-alkupvm" BETWEEN :voimassaolon-alkupvm::DATE AND :voimassaolon-loppupvm::DATE)
           OR (:voimassaolon-loppupvm::DATE IS NOT NULL AND tl."voimassaolon-loppupvm" BETWEEN :voimassaolon-alkupvm::DATE AND :voimassaolon-loppupvm::DATE)
           OR (:voimassaolon-loppupvm::DATE IS NOT NULL AND tl."voimassaolon-alkupvm" <= :voimassaolon-alkupvm::DATE AND tl."voimassaolon-loppupvm" >= :voimassaolon-loppupvm::DATE)
      )
  -- Haetaan myöntämispäivän perusteella, kun myönnetty väli on vain alkupäivä
  AND (:myonnetty-alkupvm::DATE IS NULL OR tl.myontamispvm >= :myonnetty-alkupvm::DATE)
  -- Haetaan myöntämispäivän perusteella, kun myönnetty väli onkin vain loppupäivä
  AND (:myonnetty-loppupvm::DATE IS NULL OR tl.myontamispvm <= :myonnetty-loppupvm::DATE)
  -- Haetaan alueurakan geometrian perusteella, mikäli alueurakka on annettu
  AND (:alueurakkanro::TEXT IS NULL
    OR (s.tie IS NOT NULL AND
        st_intersects(ST_UNION(ARRAY(select a.alue FROM alueurakka a WHERE a.alueurakkanro = :alueurakkanro)),
                      CASE
                          WHEN (s.tie IS NOT NULL AND s.aosa IS NOT NULL AND s.aet IS NOT NULL AND s.losa IS NOT NULL AND s.let IS NOT NULL)
                              THEN ST_UNION(ARRAY(SELECT *
                                    FROM tierekisteriosoitteelle_viiva(
                                            CAST(s.tie AS INTEGER),
                                            CAST(s.aosa AS INTEGER),
                                            CAST(s.aet AS INTEGER),
                                            CAST(s.losa AS INTEGER),
                                            CAST(s.let AS INTEGER))))
                          WHEN (s.tie IS NOT NULL AND s.aosa IS NOT NULL AND s.aet IS NOT NULL AND s.losa IS NULL AND s.let IS NULL)
                              THEN ST_UNION(ARRAY(SELECT *
                                                  FROM tierekisteriosoitteelle_piste(
                                                          CAST(s.tie AS INTEGER),
                                                          CAST(s.aosa AS INTEGER),
                                                          CAST(s.aet AS INTEGER))))
                          ELSE NULL
                          END))
      OR (s.tie IS NULL AND s.aosa IS NULL AND s.aet IS NULL AND s.losa IS NULL AND s.let IS NULL
        AND (SELECT lower(a.nimi) as nimi FROM alueurakka a WHERE a.alueurakkanro = :alueurakkanro) ilike ANY(tl."urakoiden-nimet"))
      )
  -- Tieosoitteen perusteella
  AND (:tie::INT IS NULL OR :tie = s.tie)
  AND (:aosa::INT IS NULL OR :aosa::INT <= s.aosa)
  AND (:aet::INT IS NULL OR (:aosa::INT IS NOT NULL AND :aet <= s.aet))
  AND (:losa::INT IS NULL OR :losa::INT >= s.losa)
  AND (:let::INT IS NULL OR (:losa::INT IS NOT NULL AND :let >= s.let))
 GROUP BY tl.id, tl.myontamispvm, tl."paatoksen-diaarinumero"
 ORDER BY tl.myontamispvm DESC, tl."paatoksen-diaarinumero" DESC
 LIMIT 1000;

-- name: tielupien-hakijat
-- row-fn: muunna-hakija
SELECT DISTINCT t."hakija-nimi"
  FROM tielupa t
 WHERE t."hakija-nimi" ilike :hakuteksti;
