(ns harja.domain.laadunseuranta.sanktio
  (:require [harja.pvm :as pvm]
            [harja.domain.urakka :as urakka-domain]))

;; -> Ehtolauseilla hallitaan mitä subsettejä lajeista näytetään missäkin näkymässä mhu XXXX- #{:A :B :C ...}
;; Ehtolauseilla voidaan myös hallita miten mikäkin sanktiotyyppi asettuu tietyn lajin alle eri urakkatyyppeillä/vuosikerroilla
;; mikäli on tarve.
;; NOTE: Sanktiotyyppi-taulun tila 2022-09-21 (koodi, nimi):
;; 0,Ei tarvita sanktiotyyppiä
;; 1,Muu tuote
;; 2,Talvihoito
;; 3,Ylläpidon sakko
;; 4,Ylläpidon bonus
;; 5,Ylläpidon muistutus
;; 6,Vesiväylän sakko
;; 7,Suolasakko
;; 8,Määräpäivän ylitys
;; 9,Työn tekemättä jättäminen
;; 10,Hallinnolliset laiminlyönnit
;; 11,Muu sopimuksen vastainen toiminta
;; 12,Asiakirjamerkintöjen paikkansa pitämättömyys
;; 13,"Talvihoito, päätiet"
;; 14,"Talvihoito, muut tiet"
;; 15,Liikenneympäristön hoito
;; 16,Sorateiden hoito ja ylläpito
;; 17,Muut hoitourakan tehtäväkokonaisuudet
(defn sanktiolaji->sanktiotyyppi-koodi
  [sanktiolaji-kw urakan-alkupvm]

  (let [laji->koodi {:muistutus
                     (vec (concat
                            [;; Muu tuote, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                             #_1
                             ;; Talvihoito, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                             #_2
                             13 14]
                            ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Poistetaan urakoista 2020)
                            ;; 15, "Liikenneympäristön hoito" ja 16, "Sorateiden hoito ja ylläpito" mukana urakoissa, joiden alkuvuosi on 2019 tai pienempi.
                            (when (< (pvm/vuosi urakan-alkupvm) 2020)
                              [15 16])

                            ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Uusi, lisätään urakoille 2020)
                            ;; 17, "Muut hoitourakan tehtäväkokonaisuudet" mukana urakoissa, joiden alkuvuosi on 2020 tai suurempi
                            (when (>= (pvm/vuosi urakan-alkupvm) 2020)
                              [17])))
                     :A (vec (concat
                               [;; Muu tuote, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                                #_1
                                ;; Talvihoito, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                                #_2

                                13 14]
                               ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Poistetaan urakoista 2020)
                               ;; 15, "Liikenneympäristön hoito" ja 16, "Sorateiden hoito ja ylläpito" mukana urakoissa, joiden alkuvuosi on 2019 tai pienempi.
                               (when (< (pvm/vuosi urakan-alkupvm) 2020)
                                 [15 16])

                               ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Uusi, lisätään urakoille 2020)
                               ;; 17, "Muut hoitourakan tehtäväkokonaisuudet" mukana urakoissa, joiden alkuvuosi on 2020 tai suurempi
                               (when (>= (pvm/vuosi urakan-alkupvm) 2020)
                                 [17])))
                     :B (vec (concat
                               [;; Muu tuote, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                                #_1
                                ;; Talvihoito, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                                #_2

                                13 14]
                               ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Poistetaan urakoista 2020)
                               ;; 15, "Liikenneympäristön hoito" ja 16, "Sorateiden hoito ja ylläpito" mukana urakoissa, joiden alkuvuosi on 2019 tai pienempi.
                               (when (< (pvm/vuosi urakan-alkupvm) 2020)
                                 [15 16])

                               ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Uusi, lisätään urakoille 2020)
                               ;; 17, "Muut hoitourakan tehtäväkokonaisuudet" mukana urakoissa, joiden alkuvuosi on 2020 tai suurempi
                               (when (>= (pvm/vuosi urakan-alkupvm) 2020)
                                 [17])))

                     :C [8 9 10 11 12]

                     :pohjavesisuolan_ylitys [7]
                     :talvisuolan_ylitys [7]

                     :yllapidon_sakko [3]
                     :yllapidon_bonus [4]
                     :yllapidon_muistutus [5]

                     :vesivayla_sakko [6]
                     ;; Näitä ei ollut sanktiotyyppi-tietokantataulussa tuotannossa käytössä, joten disabloituna tässä
                     #_#_:vesivayla_bonus []
                     #_#_:vesivayla_muistutus []

                     :lupaussanktio [0]
                     :tenttikeskiarvo-sanktio [0]
                     :testikeskiarvo-sanktio [0]
                     :vaihtosanktio [0]
                     :arvonvahennyssanktio [0]}]
    (laji->koodi sanktiolaji-kw)))

(defn sanktiolaji->sanktiotyypit
  "Suodattaa kaikki-sanktiotyypit sekvenssistä lajin ja lajiin kuuluvien sanktiotyyppien koodien avulla
  koodia vastaavat mapit. 'Kaikki-sanktiotyypit' sisältää sanktiotyyppien kaikki tiedot mappeina.

  Laji -> tyyppi hierarkia on siis määritelty esim. näin: {:A [1 8 12]}, jossa numerot ovat sanktiotyyppien uniikkeja koodeja.
  Tietokannassa on sanktiotyyppien koodeilla löytyvät tiedot esim. {:nimi ... :id ... :toimenpidekoodi ... :koodi ...}

  Käyttöliittymän valikkoja varten tämä funktio huolehtii myös siitä, että funktio paluuarvona
  palautetaan sanktiotyypit vastaavassa järjestyksessä, kuin \"sanktiolaji->sanktiotyyppi-koodi\" mapissa on määritelty.

  Parametrit:
    laji (keyword) Sanktiolaji
    kaikki-sanktiotyypit (seq) Sekvenssi sanktiotyyppien dataa sisältäviä mappeja. Esim. [{:nimi ... :id ... :toimenpidekoodi ... :koodi ...}]"
  [laji-kw kaikki-sanktiotyypit urakan-alkupvm]
  (let [lajin-sanktiotyyppien-koodit (sanktiolaji->sanktiotyyppi-koodi laji-kw urakan-alkupvm)]
    (->>
      (filter
        #(some #{(:koodi %)}
           lajin-sanktiotyyppien-koodit)
        kaikki-sanktiotyypit)
      ;; Varmistetaan, että lopputulos on samassa järjestyksessä kuin alkuperäinen lajin-sanktiotyyppien-koodit
      (sort-by #(.indexOf (vec lajin-sanktiotyyppien-koodit) (:koodi %))))))

(defn urakan-sanktiolajit
  "Palauttaa urakalle kuuluvat sanktiolajit Figma-speksin mukaisesti järjestettynä"
  [{:keys [tyyppi alkupvm] :as urakka}]

  (cond
    ;; Sanktiolajit MH- ja Alueurakoille, joiden alkuvuosi on yhtäsuuri tai pienempi kuin 2022
    (and (or (= :teiden-hoito tyyppi) (= :hoito tyyppi)) (<= (pvm/vuosi alkupvm) 2022))
    [:muistutus :A :B :C :arvonvahennyssanktio :pohjavesisuolan_ylitys :talvisuolan_ylitys
     :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]

    ;; Sanktiolajit MH-urakoille, joiden alkuvuosi on suurempi tai yhtäsuuri kuin 2023
    ;; TODO: Speksi varmistettava (Tämä tulee eteen vielä myöhemmin. Sääntö lisätty jo etukäteen tähän tuleville 2023 urakoille.
    ;;       TODO-kommentti jätetty tulevalle kehittäjälle, joka tähän tulee perehtymään.
    (and (= :teiden-hoito tyyppi) (>= (pvm/vuosi alkupvm) 2023))
    [:muistutus :A :B :C :arvonvahennyssanktio :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]

    ;; Yllapidon urakka?
    (urakka-domain/yllapidon-urakka? tyyppi)
    [:yllapidon_sakko :yllapidon_muistutus]

    :else []))

(defn laatupoikkeaman-sanktiolajit
  [{:keys [tyyppi alkupvm] :as urakka}]
  (cond
    ;; Yllapidon urakka?
    (urakka-domain/yllapidon-urakka? tyyppi)
    [:yllapidon_sakko :yllapidon_muistutus]

    ;; MHU ja muut
    :else [:muistutus :A :B :C :arvonvahennyssanktio]))



(def kasittelytavat [:tyomaakokous :valikatselmus :puhelin :kommentit :muu])

(defn muu-kuin-muistutus? [sanktio]
  (and (not= :muistutus (:laji sanktio))
       (not= :yllapidon_muistutus (:laji sanktio))
       (not= :vesivayla_muistutus (:laji sanktio))))

(defn sakkoryhmasta-sakko? [sanktio]
  (and (not= :muistutus (:sakkoryhma sanktio))
       (not= :yllapidon_muistutus (:sakkoryhma sanktio))))

(defn paatos-on-sanktio? [sanktio]
  (= :sanktio (get-in sanktio [:paatos :paatos])))

(def +yllapidon-sanktiofraasit+
  (sort-by
    second
    ;; ao. lista perustuu ylläpidon sopimusasiakirjoihin, ks. HAR-3683
    [[:valitavoitteen-viivastyminen "Välitavoitteen viivästyminen"]
     [:laatusuunnitelman-vastainen-toiminta "Laatusuunnitelman vastainen toiminta työmaalla"]
     [:toistuva-laatusuunnitelman-vastainen-toiminta "Toistuva laatusuunnitelman vastainen toiminta työmaalla"]
     [:laadunvalvontaan-liittyvien-mittausten-ym-toimien-laiminlyonnit "Laadunvalvontaan liittyvien mittausten ym. toimien laiminlyönnit"]
     [:ymparistoasioihin-liittyvat-laiminlyonnit "Ympäristöasioihin liittyvät laiminlyönnit"]
     [:tilaajan-pistokoe-alitus-jota-ei-urakoitsijan-laatupoikkeamaraportissa "Tilaajan pistokokeella havaittu laadunalitukset, jota ei urakoitsijan laatupoikkeamaraportissa"]
     [:ei-poikkeamaraporttia-heti-poikkeaman-havaitsemisen-jalkeen "Laatupoikkeamasta ei tehty poikkeamaraporttia  havaitsemisen jälkeen, laatupoikkeama jäi seuraavassa työvaiheessa piiloon"]
     [:tyoskentelyaikaan-tai-kohteiden-yhtajaksoiseen-valmistumiseen-liittyvat-puutteet-tai-laiminlyonnit "Työskentelyaikaan tai kohteiden yhtäjaksoiseen valmistumiseen liittyvät puutteet tai laiminlyönnit"]
     [:urakoitsija-ei-ole-toimittanut-tyovaihekohtaista-laatusuunnitelmaa-ennen-tyon-aloittamista "Urakoitsija ei toimittanut työvaihekohtaista laatusuunnitelmaa ennen työn aloittamista"]
     [:tuotevaatimusten-vastainen-toiminta-joka-vaikuttaa-lopputuotteen-toimivuuteen "Tuotevaatimusten vastainen toiminta, joka vaikuttaa lopputuotteen toimivuuteen"]
     [:urakoitsijan-laatujarjestelman-mukaisessa-asiakirjassa-todennettavasti-tosiasioita-vastaamattomia-tietoja "Urakoitsijan laatujärjestelmän mukaisessa asiakirjassa todennettavasti on kirjattu tosiasioita vastaamattomia tietoja"]
     [:liikenteenhoitoon-tyonaikaisista-liikennejarjestelyista-tiedottamiseen-tai-tyoturvallisuuteen-liittyvat-puutteet-tai-laiminlyonnit "Liikenteenhoitoon, työnaikaisista liikennejärjestelyistä tiedottamiseen tai työturvallisuuteen liittyvät puutteet tai laiminlyönnit"]
     [nil "Ei kuvausta"]]))

(defn sanktiofraasi-avaimella
  [fraasin-avain]
  (first (filter #(= (first %) (keyword fraasin-avain))
                 +yllapidon-sanktiofraasit+)))

(defn yllapidon-sanktiofraasin-nimi
  [fraasin-avain]
  (second
    (sanktiofraasi-avaimella fraasin-avain)))

(def hoidon-indeksivalinnat
  ["MAKU 2015" "MAKU 2010" "MAKU 2005"])
