-- name: hae-urakan-lupaustiedot
SELECT sit.id as "sitoutuminen-id",
       sit.pisteet AS "sitoutuminen-pisteet",
       r.id as "lupausryhma-id",
       r.otsikko as "lupausryhma-otsikko",
       r.jarjestys as "lupausryhma-jarjestys",
       r."urakan-alkuvuosi" as "lupausryhma-alkuvuosi",

       -- lupaus
       l.lupaustyyppi,
       l.jarjestys as "lupaus-jarjestys",
       l.pisteet as "lupaus-pisteet",
       l."kirjaus-kkt",
       l."paatos-kk",
       l."joustavara-kkta",
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
       LEFT JOIN lupaus_vastaus vas ON (l.id = vas."lupaus-id" AND vas."urakka-id" = :urakka)
 WHERE r."urakan-alkuvuosi" = :alkuvuosi;

-- name: lisaa-urakan-luvatut-pisteet<!
INSERT INTO lupaus_sitoutuminen ("urakka-id", pisteet, luoja)
 VALUES (:urakkaid, :pisteet, :kayttaja);

-- name: paivita-urakan-luvatut-pisteet<!
UPDATE lupaus_sitoutuminen
   SET pisteet = :pisteet, muokattu = NOW(), muokkaaja = :kayttaja
 WHERE id = :id;

-- name: hae-lupauksen-urakkatieto
SELECT "urakka-id"
  FROM lupaus_sitoutuminen
 WHERE id = :id;
