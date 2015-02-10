-- name: hae-urakan-yhteyshenkilot
-- Hakee annetun urakan kaikki yhteyshenkilöt ja päivystäjät päivystysaikoineen
SELECT y.id, y.etunimi, y.sukunimi, y.kayttajatunnus, y.tyopuhelin, y.matkapuhelin, y.sahkoposti,
       yu.rooli, yu.id as yu
  FROM yhteyshenkilo y LEFT JOIN yhteyshenkilo_urakka yu ON yu.yhteyshenkilo_id=y.id
 WHERE yu.urakka_id = :urakka

-- name: hae-paivystykset
-- Hakee päivystykset annetulle joukolle urakka<->yhteyshenkilö linkkejä
SELECT p.id, p.vastuuhenkilo, p.varahenkilo, p.alku, p.loppu, yu.yhteyshenkilo_id
  FROM paivystys p LEFT JOIN yhteyshenkilo_urakka yu ON p.yhteyshenkilo_urakka_id = yu.id
 WHERE p.yhteyshenkilo_urakka_id IN (:linkit)

 
