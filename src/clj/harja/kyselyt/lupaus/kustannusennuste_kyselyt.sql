-- Kustannusennuste-lupauksen tietokantakyselyt
-- Sisältää kustannusennusteiden tallentamiseen, hakemiseen ja päivittämiseen liittyvät kyselyt

-- name: hae-kustannusennuste-id
-- single?: true
SELECT id 
  FROM lupaus_kustannusennuste ke
 WHERE ke."lupaus-id" = :lupaus-id
   AND ke."urakka-id" = :urakka-id
   AND ke.maarapaiva = :maarapaiva;

-- name: hae-kustannusennuste
SELECT ke.id,
       ke."lupaus-id",
       ke."urakka-id", 
       ke.hoitovuosi,
       ke.maarapaiva,
       ke.ennustettu_tavoitehinta AS tavoitehinta,
       ke.ennustetut_kustannukset AS "toteutuneet-kustannukset",
       ke.syotetty_pvm,
       ke.lasketut_pisteet AS pisteet,
       ke.luoja,
       ke.muokkaaja,
       ke.luotu,
       ke.muokattu
  FROM lupaus_kustannusennuste ke
 WHERE ke."lupaus-id" = :lupaus-id
   AND ke."urakka-id" = :urakka-id  
   AND ke.maarapaiva = :maarapaiva;

-- name: lisaa-kustannusennuste<!
INSERT INTO lupaus_kustannusennuste 
            ("lupaus-id", "urakka-id", hoitovuosi, maarapaiva, 
             ennustettu_tavoitehinta, ennustetut_kustannukset, syotetty_pvm, 
             lasketut_pisteet, luoja, luotu)
     VALUES (:lupaus-id, :urakka-id, :hoitovuosi, :maarapaiva,
             :tavoitehinta, :toteutuneet-kustannukset, :syotetty-pvm,
             :pisteet, :kayttaja, NOW());

-- name: paivita-kustannusennuste<!
UPDATE lupaus_kustannusennuste
   SET ennustettu_tavoitehinta = :tavoitehinta,
       hoitovuosi = :hoitovuosi,
       ennustetut_kustannukset = :toteutuneet-kustannukset,
       syotetty_pvm = :syotetty-pvm,
       lasketut_pisteet = :pisteet,
       muokkaaja = :kayttaja,
       muokattu = NOW()
 WHERE id = :id;

-- name: hae-lupauksen-kaikki-kustannusennusteet
SELECT ke.id,
       ke."lupaus-id",
       ke."urakka-id", 
       ke.hoitovuosi,
       ke.maarapaiva,
       ke.ennustettu_tavoitehinta AS tavoitehinta,
       ke.ennustetut_kustannukset AS "toteutuneet-kustannukset",
       ke.syotetty_pvm,
       ke.lasketut_pisteet AS pisteet,
       ke.luoja,
       ke.muokkaaja,
       ke.luotu,
       ke.muokattu,
       EXTRACT(MONTH FROM ke.maarapaiva) AS maarapaiva_kk
  FROM lupaus_kustannusennuste ke
 WHERE ke."lupaus-id" = :lupaus-id
   AND ke."urakka-id" = :urakka-id
   AND ke.hoitovuosi = :hoitokauden-alkuvuosi
 ORDER BY ke.maarapaiva;

-- name: hae-lupauksen-kaikki-kustannusennusteet-kaikki-hoitovuodet
-- Hakee kaikki kustannusennusteet ILMAN hoitovuosisuodatusta
-- Käytetään välikatselmuksessa kun lasketaan pisteitä (huomioidaan offset)
SELECT ke.id,
       ke."lupaus-id",
       ke."urakka-id", 
       ke.hoitovuosi,
       ke.maarapaiva,
       ke.ennustettu_tavoitehinta AS tavoitehinta,
       ke.ennustetut_kustannukset AS "toteutuneet-kustannukset",
       ke.syotetty_pvm,
       ke.lasketut_pisteet AS pisteet,
       ke.luoja,
       ke.muokkaaja,
       ke.luotu,
       ke.muokattu,
       EXTRACT(MONTH FROM ke.maarapaiva) AS maarapaiva_kk
  FROM lupaus_kustannusennuste ke
 WHERE ke."lupaus-id" = :lupaus-id
   AND ke."urakka-id" = :urakka-id
 ORDER BY ke.maarapaiva;

-- name: hae-kustannusennuste-kuukausi-pisterajat  
-- single?: true
-- Hakee kuukauden pisterajat JSON-muodossa uudesta taulusta
SELECT kp.pisterajat
  FROM lupaus_kustannusennuste_kuukausi_pisteet kp
 WHERE kp."lupaus-id" = :lupaus-id
   AND kp.kuukausi = :kuukausi  
 ORDER BY kp.paiva;

-- name: hae-kustannusennuste-kuukausi-offset 
-- single?: true
-- Hakee kuukauden pisterajat JSON-muodossa uudesta taulusta
SELECT kp.pisteytys_hoitovuosi_offset AS "pisteytys-hoitovuosi-offset"
  FROM lupaus_kustannusennuste_kuukausi_pisteet kp
 WHERE kp."lupaus-id" = :lupaus-id
   AND kp.kuukausi = :kuukausi  
 ORDER BY kp.paiva;

-- name: hae-valikatselmuksen-vahvistetut-kustannusennusteet
SELECT pty.tavoitehinta AS "vahvistettu-tavoitehinta",
       pty.toteutuneet_kustannukset AS "vahvistetut-toteutuneet-kustannukset",
       pty.hoitokauden_alkuvuosi,
       pta.hoitokauden_lopun_tavoitehinta AS "alitus-tavoitehinta", 
       pta.toteutuneet_kustannukset AS "alitus-toteutuneet-kustannukset",
       pta.hoitokauden_alkuvuosi AS "alitus-hoitokauden-alkuvuosi"
  FROM paatos_tavoitehinta_ylitys pty
       FULL OUTER JOIN paatos_tavoitehinta_alitus pta 
                    ON pty.urakkaid = pta.urakkaid 
                   AND pty.hoitokauden_alkuvuosi = pta.hoitokauden_alkuvuosi
 WHERE (pty.urakkaid = :urakka-id OR pta.urakkaid = :urakka-id)
   AND (pty.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi OR pta.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi)
   AND (pty.poistettu = FALSE OR pty.poistettu IS NULL)
   AND (pta.poistettu = FALSE OR pta.poistettu IS NULL);

-- name: paivita-kustannusennuste-lopulliset-pisteet!
-- Päivittää kustannusennusteen lopulliset pisteet
UPDATE lupaus_kustannusennuste
   SET ennustettu_tavoitehinta = :ennustettu-tavoitehinta,
       ennustetut_kustannukset = :ennustetut-kustannukset,
       lasketut_pisteet = :lasketut-pisteet,
       tarkkuus_prosentti = :tarkkuus-prosentti,
       laskentakaava_versio = :laskentakaava-versio,
       laskentakaava_teksti = :laskentakaava-teksti,
       laskentakaava_parametrit = :laskentakaava-parametrit::jsonb,
       laskentakaava_vaiheet = :laskentakaava-vaiheet::jsonb,
       muokkaaja = :muokkaaja,
       muokattu = NOW()
 WHERE id = :kustannusennuste-id;

-- name: onko-kustannusennuste-pisteet-laskettu
SELECT COUNT(*) AS yhteensa,
       COUNT(ke.lasketut_pisteet) AS laskettu_pisteet,
       CASE 
         WHEN COUNT(*) = 0 THEN false
         WHEN COUNT(ke.lasketut_pisteet) = COUNT(*) THEN true 
         ELSE false 
       END AS kaikki_laskettu
  FROM lupaus_kustannusennuste ke
       INNER JOIN lupaus l 
               ON l.id = ke."lupaus-id"  
 WHERE ke."urakka-id" = :urakka-id
   AND ke.hoitovuosi = :hoitokauden-alkuvuosi
   AND l.lupaustyyppi = 'kustannusennuste';

-- name: hae-kustannusennuste-maarapaivat
-- Hakee kustannusennusteen määräpäivätiedot urakan alkuvuoden perusteella
SELECT kuukausi, 
       paiva, 
       kuvaus,
       (:hoitokauden-alkuvuosi + CASE WHEN kuukausi >= 10 THEN 0 ELSE 1 END)::INTEGER AS vuosi,
       MAKE_DATE((:hoitokauden-alkuvuosi + CASE WHEN kuukausi >= 10 THEN 0 ELSE 1 END)::INTEGER, kuukausi, paiva) AS maarapaiva_pvm
  FROM lupaus_kustannusennuste_kuukausi_pisteet 
 WHERE "lupaus-id" = :lupaus-id
 ORDER BY kuukausi;

-- name: hae-urakat-joilla-kustannusennuste
-- Hakee kaikki teiden-hoito urakat joilla on kustannusennuste-lupaus (testaustyökalu)
SELECT DISTINCT u.id AS "urakka-id",
       u.nimi AS "urakka-nimi", 
       u.alkupvm,
       u.loppupvm
  FROM urakka u
       JOIN lupausryhma_urakka lu 
         ON u.id = lu."urakka_id"
       JOIN lupausryhma r 
         ON lu."lupausryhma_id" = r.id
       JOIN lupaus l 
         ON r.id = l."lupausryhma-id"
 WHERE u.tyyppi = 'teiden-hoito'
   AND u.poistettu = FALSE
   AND l.lupaustyyppi = 'kustannusennuste'::lupaustyyppi
 ORDER BY u.nimi;

-- name: hae-urakan-kaikki-kustannusennusteet-testaus
-- Hakee kaikki kustannusennusteet hoitokaudelle testaustyökalua varten
-- Sisältää myös pisterajat kuukauden perusteella ja laskentakaavan tiedot
SELECT ke.id,
       ke.hoitovuosi,
       ke.maarapaiva,
       ke.ennustettu_tavoitehinta AS "ennustettu_tavoitehinta",
       ke.ennustetut_kustannukset AS "ennustetut_kustannukset",
       ke.lasketut_pisteet,
       ke.tarkkuus_prosentti,
       (ke.maarapaiva < NOW()) AS "syotetty_ajoissa",
       kp.pisterajat,
       EXTRACT(MONTH FROM ke.maarapaiva) AS "kuukausi",
       ke.laskentakaava_teksti AS "laskentakaava-teksti",
       ke.laskentakaava_parametrit,
       ke.laskentakaava_vaiheet
  FROM lupaus_kustannusennuste ke
       LEFT JOIN lupaus_kustannusennuste_kuukausi_pisteet kp 
              ON ke."lupaus-id" = kp."lupaus-id"
             AND EXTRACT(MONTH FROM ke.maarapaiva) = kp.kuukausi
 WHERE ke."urakka-id" = :urakka-id
   AND ke.hoitovuosi = :hoitokauden-alkuvuosi
 ORDER BY ke.maarapaiva;

-- name: hae-urakan-kaikki-kustannusennusteet-testaus-kaikki-hoitovuodet
-- Hakee KAIKKI urakan kustannusennusteet ILMAN hoitovuosisuodatusta testaustyökalua varten
-- Käytetään kun halutaan nähdä kaikki ennusteet huomioiden offset-logiikka
SELECT ke.id,
       ke.hoitovuosi,
       ke.maarapaiva,
       ke.ennustettu_tavoitehinta AS "ennustettu_tavoitehinta",
       ke.ennustetut_kustannukset AS "ennustetut_kustannukset",
       ke.lasketut_pisteet,
       ke.tarkkuus_prosentti,
       (ke.maarapaiva < NOW()) AS "syotetty_ajoissa",
       kp.pisterajat,
       EXTRACT(MONTH FROM ke.maarapaiva) AS "kuukausi",
       ke.laskentakaava_teksti AS "laskentakaava-teksti",
       ke.laskentakaava_parametrit,
       ke.laskentakaava_vaiheet
  FROM lupaus_kustannusennuste ke
       LEFT JOIN lupaus_kustannusennuste_kuukausi_pisteet kp 
              ON ke."lupaus-id" = kp."lupaus-id"
             AND EXTRACT(MONTH FROM ke.maarapaiva) = kp.kuukausi
 WHERE ke."urakka-id" = :urakka-id
   AND ke."lupaus-id" = :lupaus-id
 ORDER BY ke.maarapaiva;

-- name: hae-poistettavien-kustannusennusteiden-lkm
-- Laskee kuinka monta kustannusennustetta poistetaan
SELECT COUNT(*) AS kpl
  FROM lupaus_kustannusennuste
 WHERE "urakka-id" = :urakka-id
   AND hoitovuosi = :hoitokauden-alkuvuosi;

-- name: poista-urakan-hoitokauden-kustannusennusteet!
-- Poistaa kaikki urakan hoitokauden kustannusennusteet (testaustyökalu)
DELETE FROM lupaus_kustannusennuste
      WHERE "urakka-id" = :urakka-id
        AND hoitovuosi = :hoitokauden-alkuvuosi;

-- name: hae-urakan-kustannusennuste-lupaus-id
-- single?: true
-- Hakee urakan ensimmäisen kustannusennuste-lupauksen ID:n (testaustyökalu)
SELECT l.id AS "lupaus-id"
  FROM lupaus l
       JOIN lupausryhma r 
         ON l."lupausryhma-id" = r.id
       JOIN lupausryhma_urakka lu 
         ON r.id = lu.lupausryhma_id
 WHERE lu."urakka_id" = :urakka-id
   AND l.lupaustyyppi = 'kustannusennuste'::lupaustyyppi
 ORDER BY l.jarjestys
 LIMIT 1;
