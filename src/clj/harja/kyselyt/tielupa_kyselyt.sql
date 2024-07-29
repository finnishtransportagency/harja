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
  -- Logiikka on rakennettu niin, että annetetut parametrit täsmäävät täysin tai tieosoite jää ikään kuin "väliin".
  AND (:tie::INT IS NULL OR :tie::INT = s.tie) -- Tien pitää täsmätä
  AND (:aosa::INT IS NULL OR :aosa::INT <= s.aosa) -- Jos alkuosa on annettu, niin otetaan sama alku osa tai suurempi
  -- Jos alkuetäisyys on annettu, niin varmista, että alkuosakin on annettu.
  -- Jos alkuosa on sama, niin alkuetäisyys vaikuttaa.
  -- Jos alkuosa tulee myöhemmin, niin alkuetäisyydellä ei ole enää merkitystä
  AND (:aet::INT IS NULL
       OR (:aosa::INT IS NOT NULL AND :aosa::INT = s.aosa AND s.aet >= :aet::INT)
       OR (:aosa::INT IS NOT NULL AND s.aosa > :aosa::INT)) -- Alukuetäisyydellä ei merkitystä

  -- Jos loppuosa annettu, niin hyödynnetään sitä, mikäli tieluvalla on loppuosa. Hyödyntäessä otetaan ne joissa sama tai pienempi loppuosa.
  -- Samalla varmistetaan, että alkuosa ei ole suurempi, kuin haussa annettu loppuosa
  -- Tämä tarkoittaa sitä, että jos tieluvassa tieosoite on annettu "laskevassa" järjestyksessä, eli että loppuosa on pienempi, kuin alkuosa
  -- niin tämä haku ei löydä sellaista ellei tieluvan loppuosa täsmää täsmälleen annettuun loppuosaan.
  AND (:losa::INT IS NULL
           OR (:losa::INT IS NOT NULL AND s.losa IS NULL AND s.aosa <= :losa::INT )
           OR (:losa::INT IS NOT NULL AND (s.aosa <= :losa::INT OR s.aosa > s.losa) AND s.losa IS NOT NULL AND :losa::INT >= s.losa))

  -- Loppuetäisyys vaikuttaa, mikäli loppuosa on sama. Jos loppuosa on pienempi, ei loppuetäisyydellä ole merkitystä
  AND (:let::INT IS NULL OR s.losa IS NULL
       OR (:let::INT IS NOT NULL AND :losa::INT IS NOT NULL AND s.losa IS NOT NULL AND :losa::INT = s.losa AND s.let <= :let::INT)
       OR (:losa::INT IS NULL OR (s.losa IS NOT NULL AND s.losa < :losa::INT)))
 GROUP BY tl.id, tl.myontamispvm, tl."paatoksen-diaarinumero"
 ORDER BY tl.myontamispvm DESC, tl."paatoksen-diaarinumero" DESC
 LIMIT 1000;

-- name: tielupien-hakijat
-- row-fn: muunna-hakija
SELECT DISTINCT t."hakija-nimi"
  FROM tielupa t
 WHERE t."hakija-nimi" ilike :hakuteksti;
