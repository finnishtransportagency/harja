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
        "voimassaolon-loppupvm", tyyppi, "hakija-nimi", "paatoksen-diaarinumero",
       jsonb_agg(row_to_json(row(s.tie, s.aosa, s.aet, s.losa, s.let, s.geometria))) as sijainnit
 FROM tielupa tl,
      unnest(tl.sijainnit) s
WHERE (:urakka-id::INTEGER IS NULL OR :urakka-id = ANY(tl.urakat))
  AND (:hakija-nimi::TEXT IS NULL OR upper(tl."hakija-nimi") ilike upper(:hakija-nimi))
  AND (:tyyppi::tielupatyyppi IS NULL OR tl.tyyppi = :tyyppi)
  AND (:paatoksen-diaarinumero::TEXT IS NULL OR tl."paatoksen-diaarinumero" = :paatoksen-diaarinumero)
  -- Hae voimassaolo pelkästään alkupäivän perusteella eli loppuehtoa ei ole annettu ja voimassa olo on suurempi kuin annettu päivämäärä
  -- Tai hae voimassaolo sekä alkupäivän, että loppupäivän perusteella
  AND (:voimassaolon-alkupvm::DATE IS NULL
           OR (:voimassaolon-loppupvm::DATE IS NULL AND tl."voimassaolon-alkupvm" >= :voimassaolon-alkupvm::DATE)
           OR (:voimassaolon-loppupvm::DATE IS NOT NULL AND tl."voimassaolon-alkupvm" >= :voimassaolon-alkupvm::DATE AND tl."voimassaolon-loppupvm" <= :voimassaolon-loppupvm::DATE))
  -- Edellisessä on käsitelty alkupäivän perusteella kaikki tilanteet. Tässä lisäyksenä haku pelkästään loppupäivän perusteella
  AND (:voimassaolon-loppupvm::DATE IS NULL OR tl."voimassaolon-loppupvm" <= :voimassaolon-loppupvm::DATE)
  -- Haetaan myöntämispäivän perusteella, kun myönnetty väli on vain alkupäivä
  AND (:myonnetty-alkupvm::DATE IS NULL OR tl.myontamispvm >= :myonnetty-alkupvm::DATE)
  -- Haetaan myöntämispäivän perusteella, kun myönnetty väli onkin vain loppupäivä
  AND (:myonnetty-loppupvm::DATE IS NULL OR tl.myontamispvm <= :myonnetty-loppupvm::DATE)
  -- Haku myös tieosoiteella, mutta se hetken päästä
  AND (:organisaatio-id::TEXT IS NULL
    OR (st_intersects(ST_UNION(ARRAY(select o.alue FROM organisaatio o WHERE o.id = :organisaatio-id)),
                      CASE
                          WHEN s.tie IS NOT NULL
                              THEN ST_UNION(ARRAY(SELECT *
                                    FROM tierekisteriosoitteelle_viiva(
                                            CAST(s.tie AS INTEGER),
                                            CAST(s.aosa AS INTEGER),
                                            CAST(s.aet AS INTEGER),
                                            CAST(s.losa AS INTEGER),
                                            CAST(s.let AS INTEGER))))
                          ELSE NULL
                          END)
          )
    )
 GROUP BY tl.id;