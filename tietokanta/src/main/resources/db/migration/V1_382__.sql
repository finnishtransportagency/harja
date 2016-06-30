<<<<<<< HEAD
-- 1. Lisää puuttuvat tehtävät

-- Talvihoito

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES (
  'Aurausviitoitus ja kinostimet', 4, now(), 'jkm', 'aurausviitoitus ja kinostimet',
  ARRAY ['kokonaishintainen'] :: hinnoittelutyyppi [],
  (SELECT id
   FROM toimenpidekoodi
   WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND emo = (SELECT id
                                                           FROM toimenpidekoodi
                                                           WHERE nimi = 'Talvihoito' AND taso = 2)));

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES (
  'Lumen siirto', 4, now(), 'jkm', 'lumensiirto',
  ARRAY ['kokonaishintainen'] :: hinnoittelutyyppi [],
  (SELECT id
   FROM toimenpidekoodi
   WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND emo = (SELECT id
                                                           FROM toimenpidekoodi
                                                           WHERE nimi = 'Talvihoito' AND taso = 2)));

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES (
  'Paannejään poisto', 4, now(), 'jkm', 'paannejaan poisto',
  ARRAY ['kokonaishintainen'] :: hinnoittelutyyppi [],
  (SELECT id
   FROM toimenpidekoodi
   WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND emo = (SELECT id
                                                           FROM toimenpidekoodi
                                                           WHERE nimi = 'Talvihoito' AND taso = 2)));

-- Liikenneympäristön hoito

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES (
  'Puiden ja pensaiden hoito', 4, now(), NULL, NULL,
  ARRAY ['kokonaishintainen'] :: hinnoittelutyyppi [],
  (SELECT id
   FROM toimenpidekoodi
   WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND emo = (SELECT id
                                                           FROM toimenpidekoodi
                                                           WHERE nimi = 'Liikenneympäristön hoito' AND taso = 2)));

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES (
  'Muu liikenneympäristön hoito', 4, now(), NULL, NULL,
  ARRAY ['kokonaishintainen'] :: hinnoittelutyyppi [],
  (SELECT id
   FROM toimenpidekoodi
   WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND emo = (SELECT id
                                                           FROM toimenpidekoodi
                                                           WHERE nimi = 'Liikenneympäristön hoito' AND taso = 2)));

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES (
  'Nurmetuksen hoito / niitto', 4, now(), NULL, NULL,
  ARRAY ['kokonaishintainen'] :: hinnoittelutyyppi [],
  (SELECT id
   FROM toimenpidekoodi
   WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND emo = (SELECT id
                                                           FROM toimenpidekoodi
                                                           WHERE nimi = 'Liikenneympäristön hoito' AND taso = 2)));

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES (
  'Siltojen ja laitureiden puhdistus', 4, now(), NULL, NULL,
  ARRAY ['kokonaishintainen'] :: hinnoittelutyyppi [],
  (SELECT id
   FROM toimenpidekoodi
   WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND emo = (SELECT id
                                                           FROM toimenpidekoodi
                                                           WHERE nimi = 'Liikenneympäristön hoito' AND taso = 2)));

-- Soratien hoito
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES (
  'Sorateiden pinnan hoito', 4, now(), 'jkm', NULL,
  ARRAY ['kokonaishintainen'] :: hinnoittelutyyppi [],
  (SELECT id
   FROM toimenpidekoodi
   WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND emo = (SELECT id
                                                           FROM toimenpidekoodi
                                                           WHERE nimi = 'Soratien hoito' AND taso = 2)));

-- 2. Lisää API-seurannalle lippu

ALTER TABLE toimenpidekoodi ADD COLUMN api_seuranta BOOLEAN;

-- 3. Merkitse kokonaishintaiset tehtävät API-seurantaan
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Auraus ja sohjonpoisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Liikennemerkkien puhdistus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Linjahiekoitus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lumivallien madaltaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pinnan tasaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pistehiekoitus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sulamisveden haittojen torjunta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Suolaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Aurausviitoitus ja kinostimet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lumen siirto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Paannejään poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Liikennemerkkien, opasteiden ja liikenteenohjauslaitteiden hoito sekä reunapaalujen kp';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Porttaalien tarkastus ja huolto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden palteen poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden sorapientareen täyttö';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'L- ja p-alueiden puhdistus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Puiden ja pensaiden hoito';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Roskien poisto ja kevätsiivoukset muut tiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Roskien poisto ja kevätsiivoukset taajamat, kevarit ja päätiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Roskien poisto ja kevätsiivoukset L- ja P-alueilta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Risteysalueiden näkemäraivaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Muut viheralueiden hoitoon liittyvät työt';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Koneellinen niitto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Nurmetuksen hoito / niitto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Koneellinen vesakonraivaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Luvattomien mainosten poistaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Maastopalvelu';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Meluesteiden pesu';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Töherrysten poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valvontakameratolppien puhdistus/tarkistus keväisin';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Muu liikenneympäristön hoito';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Siltojen ja laitureiden puhdistus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Siltojen vuositarkastus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen aukaisu ja muut rumpujen hoitoon liittyvät työt';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Viranomaistehtävissä avustaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorateiden pinnan hoito';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorateiden muokkaushöyläys';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorateiden pölynsidonta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorastus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Harjaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Harjaus ja roskien poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten ja levähdysalueiden varusteiden korjaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten uusiminen, taajamassa';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten uusiminen, maaseudulla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten ja levähdysalueiden varusteiden uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten hoito, vilkkaat sis. talvi- ja kesähoidon sekä rakenteiden huoltamisen (uudet)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten hoito, tavanomaiset sis. talvi- ja kesähoidon sekä rakenteiden huoltamisen (uudet)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten tarkastus, kunnostus ja huoltomaalaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten oikaisu';

-- 4. Merkiste yksikköhintaiset tehtävät API-seurantaan

UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sulkualueet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'TTT TosiTarkkaTiemerkintä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korj./uusiminen/rakentaminen ilman siirtymäkiilaa';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tunnelin sadevesialtaan sakkapesän puhdistus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tunnelin vikakorjaukset, 2 rakennusammattimies';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystetyn tien avo-ojitus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Avo-ojitus / päällystetyt muut tiet ja soratiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen ohjelmoitu uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus ja uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Porttaalien vuositarkastus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tunnelin vikakorjaukset, 1 rakennusammattimies';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen, merkin vaihto tukirakenteineen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus (ml.sillat ja siltapaikat) -saumojen juottaminen bitumilla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 800 <= 1000 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Linjamerkinnät massalla, paksuus 3 mm:Sulkuviiva ja varoitusviiva keltainen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Linjamerkinnät massalla, paksuus 3 mm:Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm:Pyörätien jatkeet ja suojatiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Muut pienmerkinnät';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Nuolet ja nopeusrajoitusmerkinnät ja väistämisviivat';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Linjamerkinnän upotusjyrsintä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen ryhmävaihto:SpNa 50 - 100 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen ryhmävaihto:SpNa 150 - 400 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Laskuojat/päällystetyt tiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kaiteiden nostaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien runkokelirikkokorjaukset kohde 1';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kaiteiden kunnostaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Varareittien kiertotieopastustaulut';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kunnostettavat esteettömyyskohteet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kiertotieopasteet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kaiteiden purkaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Siltojen lumiverkkojen asennus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden rumpujen ohjelmoitu uusiminen 1200 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden rumpujen ohjelmoitu uusiminen 1000 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden rumpujen ohjelmoitu uusiminen 800 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Avo-ojitus / päällystetyt vt-, kt- ja seututiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystetyn tien avo-ojitus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Maakivien poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen ryhmävaihto:PpNa 35 - 180 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm:Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm:Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen ryhmävaihto:Monimetalli 35 - 150 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen ryhmävaihto:Monimetalli 250 - 400 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen ryhmävaihto:Loistelamppu';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen yksittäisvaihto:SpNa 50 - 100 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen yksittäisvaihto:SpNa 150 - 400 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen yksittäisvaihto:Hg 50 - 400 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen yksittäisvaihto:Sytytin';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen yksittäisvaihto:PpNa 35  - 180 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen yksittäisvaihto:Monimetalli 35 - 400 W';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen yksittäisvaihto:Loistelamppu';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lamppujen yksittäisvaihto:LED-yksikkö VP 2221/2223 valaisimeen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Jalustan vaihto SJ 4';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Teräskartiopylväs HE3 h=10m V=2, 5m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Myötäävä puupylväs h=10m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Puupylväsvarsi V= 1.0 - 2, 5m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Valaisin SpNa 50 - 70 W, lamppuineen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Valaisin SpNa 100 - 250 W, lamppuineen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Valaisin SpNa 100 - 250 W, 2-tehokuristin ja tehonvaihtorele, lamppuineen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Valaisin LED, h=6 m, K4';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Valaisin LED, h=10 m, AL4a';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Valaisin LED VP 2221/2223 M1 - M3';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Maajakokeskus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden vaihto:Haruksen uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden korjaus:Puupylvään oikaisu';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden korjaus:Metallipylvään oikaisu alle 13 m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden korjaus:Metallipylvään oikaisu yli 13 m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden korjaus:Valaisinvarsien suuntaus alle 13 m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Valaistuslaitteiden korjaus:Valaisinvarsien suuntaus yli 13 m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Asentaja';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tr- kaivuri';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Törmäysvaimennin';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kuorma-auto nosturilla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Nostolava alle 13 m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Nostolava yli 13 m';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Graffitien poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteen korjaus mastiksilla siltakohteiden heitoissa';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus - konetiivistetty -valuasfaltti';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen portaaliin';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Puun poisto raivausjätteineen (taajamassa)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi =
      'Täristävät merkinnät:sini-aallonmuotoinen jyrsintä, reunaviiva, 2 ajr tie:lev 30 cm, aallonpit 60 cm, syv 6 mm aallonharjalla, syv 13 mm aallon pohjalla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi =
      'Täristävät merkinnät:sini-aallonmuotoinen jyrsintä, reunaviiva, 1 ajr tie:lev 30 cm, aallonpit 60 cm, syv 6 mm aallonharjalla, syv 13 mm aallon pohjalla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi =
      'Täristävät merkinnät:sylinterijyrsintä, reunaviiva, 2 ajr tie:lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi =
      'Täristävät merkinnät:sylinterijyrsintä, keskiviiva, 1 ajr tie:lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi =
      'Täristävät merkinnät:sylinterijyrsintä, reunaviiva, 1 ajr tie:lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm:Nopeusrajoitusmerkinnät';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorastus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm:Väistämisviivan yksi kolmio hainhammas';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm:Pysäytysviiva';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm:Sulkualueet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm:Pyörätien jatkeet ja suojatiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm:Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm:Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Linjamerkinnät massalla paksuus 7 mm:Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Linjamerkinnät massalla paksuus 7 mm:Reunaviiva ja reunaviivan jatke';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Linjamerkinnät massalla, paksuus 3mm:Reunaviiva ja reunaviivan jatke';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Reunapalkin ja päällysteen väl.sauman tiivistäminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Hylättyjen ajoneuvojen siirto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Puun poisto raivausjätteineen (taajaman ulkopuolella)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tunnelin pumppaamon poistoputken pään laskuojan huolto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pensaiden poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tunnelin pesu ja muu puhtaanapito';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Metsähakkuu tienvarresta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Vesakonraivaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tunnelin paikalliskeskuksen hoito';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tunnelin laitteiden toimivuuden testaukset';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sisääntuloteiden siisteyden varmistaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi =
      'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen/uuden asentaminen tukirakenteineen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Avattavan sillan varaosat';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Avattavien siltojen viankorjaukset';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'L- ja P-opasteet (sis.putket, jalustat)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Käymälöiden poisto L- ja P-alueilta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Reunapaalujen uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Liikennesaarekkeen kunnostus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kolmannen osapuolen vahinkojen korjaukset';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Liikennesaarekkeen uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorateiden kaventaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kelirikon hoito ja routaheitt.tas.mursk.';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten uusiminen, taajamassa';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatosten uusiminen, maaseudulla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatoksen uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kaiteiden uusiminen/rakentaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kaiteiden uusiminen (tuiskukaiteeksi)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kaiteiden uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kaiteen rakentaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'L- ja P-alueiden liikennemerkkien rakentaminen tukirakenteineen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorateiden avo-ojitus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus - kuumapäällyste';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus - Konetiivistetty massasaumaus 20 cm leveä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'L&P-alueiden purkaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen rakentaminen sorateille siirtymäkiilalla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'SIP paikkaus (kesto+kylmä)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Siltojen vuositarkastus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sillan päällysteen halkeaman sulkeminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen, pelkkä merkki';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Runkopuiden täydennysistutus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Opastustaulujen ja liikennemerkkien rakentaminen tukirakenteineen (sis.liikennemerkkien poistamisia)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Metsän harvennus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Opastinviitan tai -taulun uusiminen ja lisääminen -ajoradan yläpuoliset opasteet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kuumapäällyste, valuasfaltti';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kuumapäällyste, ab käsityönä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus (ml.sillat ja siltapaikat) -konetivistetty valuasvaltti';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sillan päällysteen halkeaman avarrussaumaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sillan kannen päällysteen päätysauman korjaukset';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Reunapalkin ja päällysteen välisen sauman tiivistäminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Jäätien hoito';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kolmansien osapuolien vahinkojen korjaukset';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus - kylmäpäällyste ml.SOP';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Töherrysten estokäsittely';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Töherrysten poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Yksikkohintainen esimerkkitehtävä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Lisäkilvet L- ja P- alueille';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kaiteiden jatkaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatoksen poistaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pysäkkikatoksen korjaaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus -saumojen juottaminen mastiksilla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden pölynsidonta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus - valuasfaltti';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tierakenteet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus ja uusiminen  Ø> 600 - 1000 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus ja uusiminen  ≤Ø 600 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Tunnelin vikakorjaukset, 2 rakennusammattimiestä + nostolava';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden palteiden poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus, kylmäpäällyste';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus/uusiminen/rakentaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kuumapäällyste, valuasfaltti';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus ja uusiminen <= 600 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rummun uusiminen >600 kiiloineen (max 1000) sorateillä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rummun uusiminen >600 ilman kiiloja (max 1000) sorateillä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Laaja toimenpide';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden pölynsidonta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Varusteet ja laitteet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorastus tilaajan materiaalista';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Liikenteen varmistaminen erik.tilanteissa';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Oja- ja luiskameteriaalin käyttö kulutuskerrokseeen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Laskuojat/päällystetyt tiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus ja uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen ohjelmoitu uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korj./uusiminen/rakentaminen ilman siirtymäkiilaa';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus/uusiminen/rakentaminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus ja uusiminen <= 600 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien runkokelirikkokorjaukset 2';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien runkokelirikkokorjaukset 3';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien runkokelirikkokorjaukset 4';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien runkokelirikkokorjaukset 5';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien runkokelirikkokorjaukset 1';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Laaja toimenpide';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Liikenneturvallisuuskohteet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus (ml.sillat ja siltapaikat) -puhallus SIP';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus (ml.sillat ja siltapaikat) -kylmäpäällyste ml.SOP';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -portaalissa olevan viitan/opastetaulun uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus (ml.sillat ja siltapaikat) - kuumapäällyste';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus (ml.sillat ja siltapaikat) - massasaumaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteen purku/ muuttaminen soratieksi';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Kallion leikkaus ja maakivien poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Ojitusmaiden ajo läjitykseen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Laskuojat/soratiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø> 800 <=1000 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus (ml.sillat ja siltapaikat) - valuasvaltti';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteissa';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen -pelkät merkit';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen -merkin vaihto tukirakenteineen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Oja- ja luiskamateriaalien käyttö kulutuskerrokseen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø> 1000 <= 1200 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen Ø> 1000 <=1200 mm';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Avo-ojitus / päällystetyt tiet';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Runkopuiden poisto';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Katupölynsidonta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Konetiivistetty massasaumaus 10 cm leveä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus - saumojen juottaminen mastiksilla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Huonokuntoisten viittojen ja opastetaulujen uusiminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden sorapientareen kunnossapito';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Meluaidan maalaus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus - saumojen juottaminen bitumilla';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Fiskarsin tunnelin varaosat';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Maastopartio (auto + 2 ram)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorapintaisten jk+pp -teiden kesähoito';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Sorateiden avo-ojitus (kaapeli kaivualueella)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden pölynsidonta';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällystettyjen teiden sorapientareen täyttö';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Pensaiden täydennysistutus';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Konetiivistetty massasaumaus 20 cm leveä';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Reunapalkin liikuntasauman tiivistäminen';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)';
UPDATE toimenpidekoodi
SET api_seuranta = TRUE
WHERE nimi = 'Päällysteiden paikkaus - massasaumaus';
=======
-- Lisää asiatarkastuksen tiedot POT-lomakkeelle
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_pvm DATE;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_tarkastaja VARCHAR(1024);
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_tekninen_osa BOOLEAN;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_taloudellinen_osa BOOLEAN;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_lisatiedot VARCHAR(4096);
>>>>>>> develop
