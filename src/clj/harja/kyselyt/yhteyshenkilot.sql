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

-- name: luo-yhteyshenkilo<!
-- Tekee uuden yhteys
INSERT INTO yhteyshenkilo (rooli, etunimi,sukunimi,tyopuhelin,matkapuhelin,sahkoposti,organisaatio)
     VALUES (:rooli, :etu, :suku, :tyopuh, :matkapuh, :email, :org)

-- name: aseta-yhteyshenkilon-rooli!
UPDATE yhteyshenkilo_urakka
   SET rooli=:rooli
 WHERE yhteyshenkilo=:id AND urakka=:urakka
 
-- name: liita-yhteyshenkilo-urakkaan<!
-- Liittää yhteyshenkilön urakkaan
INSERT INTO yhteyshenkilo_urakka (rooli, yhteyshenkilo, urakka) VALUES (:rooli, :yht, :urakka)

-- name: paivita-yhteyshenkilo!
-- Päivittää yhteyshenkilön tiedot
UPDATE yhteyshenkilo
   SET etunimi=:etu, sukunimi=:suku, tyopuhelin=:tyopuh, matkapuhelin=:matkapuh,
       sahkoposti=:email, organisaatio=:org
 WHERE id = :id

-- name: hae-urakan-yhteyshenkilo-idt
-- Hakee yhteyshenkilöiden id, jotka ovat liitetty annettuun urakkaan
SELECT yhteyshenkilo FROM yhteyshenkilo_urakka WHERE urakka = :urakka
