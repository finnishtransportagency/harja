-- name: hae-urakan-yhteyshenkilot
-- Hakee annetun urakan kaikki yhteyshenkilöt, sekä urakoitsijan että tilaajan puolelta
SELECT y.id, y.etunimi, y.sukunimi, y.kayttajatunnus, y.tyopuhelin, y.matkapuhelin, y.sahkoposti,
       yu.rooli, yu.id as yu,
       org.id as organisaatio_id, org.nimi as organisaatio_nimi, org.tyyppi as organisaatio_tyyppi
  FROM yhteyshenkilo y
       LEFT JOIN yhteyshenkilo_urakka yu ON yu.yhteyshenkilo=y.id
       LEFT JOIN organisaatio org ON y.organisaatio = org.id
 WHERE yu.urakka = :urakka

-- name: hae-paivystykset
-- Hakee päivystykset annetulle joukolle urakka<->yhteyshenkilö linkkejä
SELECT p.id, p.vastuuhenkilo, p.varahenkilo, p.alku, p.loppu, yu.yhteyshenkilo
  FROM paivystys p LEFT JOIN yhteyshenkilo_urakka yu ON p.yhteyshenkilo_urakka = yu.id
 WHERE p.yhteyshenkilo_urakka IN (:linkit)

-- name: hae-yhteyshenkilotyypit
-- Hakee käytetyt yhteyshenkilötyypit
SELECT DISTINCT(rooli) FROM yhteyshenkilo_urakka

 
