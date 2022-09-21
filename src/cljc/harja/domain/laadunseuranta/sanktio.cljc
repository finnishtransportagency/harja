(ns harja.domain.laadunseuranta.sanktio
  (:require [harja.pvm :as pvm]))

;; -> Ehtolauseilla hallitaan mitä subsettejä lajeista näytetään missäkin näkymässä mhu XXXX- #{:A :B :C ...}
;; Ehtolauseilla voidaan myös hallita miten mikäkin sanktiotyyppi asettuu tietyn lajin alle eri urakkatyyppeillä/vuosikerroilla
;; mikäli on tarve.
(def sanktiolaji->sanktiotyyppi-koodi
  {:muistutus [0 1]
   :A [0 1 13 14 15 16]
   :B [0 1 13 14 15 16]
   :C [8 9 10 11 12 15 16]
   :arvonvahennyssanktio [8]
   :pohjavesisuolan_ylitys [7]
   :talvisuolan_ylitys [7]
   :tenttikeskiarvo-sanktio [8]
   :testikeskiarvo-sanktio [8]
   :vaihtosanktio [8]

   :yllapidon_sakko [2]
   :yllapidon_bonus [3]
   :yllapidon_muistutus [4]

   :vesivayla_sakko [5]
   :vesivayla_bonus []
   :vesivayla_muistutus []

   :lupaussanktio [8]})

(defn urakan-sanktiolajit
  "Palauttaa urakalle kuuluvat sanktiolajit Figma-speksin mukaisesti järjestettynä"
  [{:keys [tyyppi alkupvm] :as urakka}]

  (cond
    ;; Sanktiolajit MH- ja Alueurakoille, joiden alkuvuosi on yhtäsuuri tai pienempi kuin 2022
    (and (or (= :teiden-hoito tyyppi) (= :hoito tyyppi)) (<= (pvm/vuosi alkupvm) 2022))
    [:muistutus :A :B :C :arvonvahennyssanktio :pohjavesisuolan_ylitys :talvisuolan_ylitys
     :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]

    ;; Sanktiolajit MH-urakoille, joiden alkuvuosi on suurempi tai yhtäsuuri kuin 2023
    ;; TODO: Varmistettava
    (and (= :teiden-hoito tyyppi) (>= (pvm/vuosi alkupvm) 2023))
    [:muistutus :A :B :C :arvonvahennyssanktio :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]

    :else []))

(def laatupoikkeaman-sanktiolajit [:A :B :C])

(defn sanktiolaji->sanktiotyypit
  "Suodattaa kaikista sanktiotyypeistä annetun lajin ja sanktiotyypin uniikin koodin avulla lajiin kuuluvat tyypit.
  Parametrit:
    laji (keyword) Sanktiolaji
    kaikki-sanktiotyypit (seq) Sekvenssi sanktiotyyppien dataa sisältäviä mappeja."
  [laji kaikki-sanktiotyypit]
  (filter
    #(some #{(:koodi %)}
       (sanktiolaji->sanktiotyyppi-koodi laji))
    kaikki-sanktiotyypit))


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