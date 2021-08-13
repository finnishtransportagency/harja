-- name: hae-lupaus
-- row-fn: muunna-lupaus
SELECT id,
       jarjestys,
       "lupausryhma-id",
       "urakka-id",
       lupaustyyppi,
       pisteet,
       "kirjaus-kkt",
       "paatos-kk",
       "joustovara-kkta",
       sisalto,
       "urakan-alkuvuosi",
       luotu
  FROM lupaus
 WHERE id = :id;

-- name: hae-urakan-lupaustiedot
-- row-fn: muunna-lupaus
SELECT sit.id as "sitoutuminen-id",
       sit.pisteet AS "sitoutuminen-pisteet",
       r.id as "lupausryhma-id",
       r.otsikko as "lupausryhma-otsikko",
       r.jarjestys as "lupausryhma-jarjestys",
       r."urakan-alkuvuosi" as "lupausryhma-alkuvuosi",

       -- lupaus
       l.lupaustyyppi,
       l.jarjestys as "lupaus-jarjestys",
       CASE WHEN l.lupaustyyppi = 'kysely'::lupaustyyppi
                THEN l.pisteet ELSE 0
           END                                  AS "kyselypisteet",
       CASE WHEN l.lupaustyyppi != 'kysely'::lupaustyyppi
                THEN l.pisteet ELSE 0
           END                                  AS "pisteet",
       l."kirjaus-kkt",
       l."paatos-kk",
       l."joustovara-kkta",
       l.sisalto,

       -- vastaukset
       vas.kuukausi,
       vas.vuosi,
       vas.vastaus,
       vas."lupaus-vaihtoehto-id",
       vas."veto-oikeutta-kaytetty",
       vas."veto-oikeus-aika"
  FROM lupausryhma r
       LEFT JOIN lupaus_sitoutuminen sit ON sit."urakka-id" = :urakka
       JOIN lupaus l ON r.id = l."lupausryhma-id"
       LEFT JOIN lupaus_vastaus vas ON (l.id = vas."lupaus-id" AND vas."urakka-id" = :urakka
      AND (concat(vas.vuosi, '-', vas.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE))
 WHERE r."urakan-alkuvuosi" = :alkuvuosi;

-- name: lisaa-urakan-luvatut-pisteet<!
INSERT INTO lupaus_sitoutuminen ("urakka-id", pisteet, luoja)
 VALUES (:urakka-id, :pisteet, :kayttaja);

-- name: paivita-urakan-luvatut-pisteet<!
UPDATE lupaus_sitoutuminen
   SET pisteet = :pisteet, muokattu = NOW(), muokkaaja = :kayttaja
 WHERE id = :id;

-- name: hae-lupauksen-urakkatieto
SELECT "urakka-id"
  FROM lupaus_sitoutuminen
 WHERE id = :id;

-- name: hae-lupaus-vastaus
SELECT id,
       "lupaus-id",
       "urakka-id",
       kuukausi,
       vuosi,
       paatos,
       vastaus,
       "lupaus-vaihtoehto-id",
       "veto-oikeutta-kaytetty",
       "veto-oikeus-aika",
       poistettu,
       muokkaaja,
       muokattu,
       luoja,
       luotu
  FROM lupaus_vastaus
 WHERE id = :id;

-- name: lisaa-lupaus-vastaus<!
INSERT INTO lupaus_vastaus
("lupaus-id",
 "urakka-id",
 kuukausi,
 vuosi,
 paatos,
 vastaus,
 "lupaus-vaihtoehto-id",
 luoja)
VALUES
(:lupaus-id,
 :urakka-id,
 :kuukausi,
 :vuosi,
 :paatos,
 :vastaus,
 :lupaus-vaihtoehto-id,
 :kayttaja-id);

-- name: paivita-lupaus-vastaus!
UPDATE lupaus_vastaus
   SET vastaus                = :vastaus,
       "lupaus-vaihtoehto-id" = :lupaus-vaihtoehto-id,
       muokkaaja              = :muokkaaja
 WHERE id = :id;

-- name: lisaa-lupaus-vastaus<!
INSERT INTO lupaus_vastaus
("lupaus-id",
 "urakka-id",
 kuukausi,
 vuosi,
 paatos,
 vastaus,
 "lupaus-vaihtoehto-id",
 luoja)
VALUES
(:lupaus-id,
 :urakka-id,
 :kuukausi,
 :vuosi,
 :paatos,
 :vastaus,
 :lupaus-vaihtoehto-id,
 :luoja);

-- name: hae-lupaus-vaihtoehto
SELECT id, "lupaus-id", vaihtoehto, pisteet
  FROM lupaus_vaihtoehto
 WHERE id = :id;
