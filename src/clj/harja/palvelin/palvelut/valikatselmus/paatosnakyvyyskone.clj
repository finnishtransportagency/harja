(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone
  (:require [clojure.string :as str]))

(def paatostyypit
  [{:nimi "Lupaukset" :tyyppi "bonus" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 1}
   {:nimi "Lupaukset" :tyyppi "sanktio" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 2}
   {:nimi "Lupaukset" :tyyppi "taytetty" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 3}
   {:nimi "Tavoitehinnan muutokset" :versio "1a" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 4}
   {:nimi "Tavoitehinnan muutokset" :versio "1b" :urakan_alkuvuosi 2021 :nakyvyys_alkaen 2021 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 5}
   {:nimi "Hoitovuoden lopun indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "A" :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU"} :jarjestys 7}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8}
   {:nimi "Tavoitehinnan alitus" :tyyppi nil :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9}
   {:nimi "Tavoitehinnan ylitys" :tyyppi "A" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 10}
   {:nimi "Tavoitehinnan ylitys" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 11}
   {:nimi "Kattohinnan ylitys" :tyyppi nil :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 12}
   {:nimi "Hoidonjohtopalkkion muutos" :tyyppi nil :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU"} :jarjestys 13}
   {:nimi "Hoidonjohtopalkkion muutos" :tyyppi nil :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 14}
   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :tyyppi nil :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU"} :jarjestys 15}
   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :tyyppi nil :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 16}])

(defn urakan-hoitotyyppi
  "Erittäin vaativat hoitourakat merkitään päätöstauluun hoitotyyppinä MHU+"
  [erittain_vaativa_hoitourakka]
  (if erittain_vaativa_hoitourakka "MHU+" "MHU"))

(defn mahdolliset-paatokset-tyypilla [mhu-tyyppi paatokset]
  (filter #(contains? (:hoitotyyppi %) mhu-tyyppi) paatokset))

(defn mahdolliset-paatokset-urakan-alkuvuodella [urakan-alkuvuosi paatokset]
  (filter #(<= (:urakan_alkuvuosi %) urakan-alkuvuosi) paatokset))

(defn mahdolliset-paatokset-nakyvyys-vuodella [kuluva-vuosi paatokset]
  (filter #(<= (:nakyvyys_alkaen %) kuluva-vuosi) paatokset))

(defn kaikki-mahdolliset-paatokset [mhu-tyyppi urakan-alkuvuosi kuluva-hoitovuosi]
  (let [paatokset paatostyypit
        mahdollset-tyypilla (mahdolliset-paatokset-tyypilla mhu-tyyppi paatokset)
        mahdolliset-aloitusvuodella (mahdolliset-paatokset-urakan-alkuvuodella urakan-alkuvuosi mahdollset-tyypilla)
        mahdolliset-kuluvalle-vuodelle (mahdolliset-paatokset-nakyvyys-vuodella kuluva-hoitovuosi mahdolliset-aloitusvuodella)]
    mahdolliset-kuluvalle-vuodelle))

(defn yhdista-mapit
  "Yhdistetään tietokannasta tulevat ja päätöskoneelta tulevat päätökset niin, että
   käytetään päätöskoneen päätöksiä, jos niitä ei ole vielä tietokannassa ja muuten tietokannan päätöksiä.
   Vertailussa käytetään :nimi avainta. Se täytyy löytyä molemmista mapeistä."
  [pk-paatokset db-paatokset]
  (let [index-map (into {} (map (fn [m] [(:nimi m) m]) db-paatokset))]
    (map (fn [m1]
           (if-let [m2 (index-map (:nimi m1))]
             m2
             m1))
         pk-paatokset)))

(defn valmistele-lupauspaatokset [paatokset toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjouksen-tavoitehinta]
  ;; Ota mukaan oikea lupauspäätös, jos ehdot täyttyvät
  (if (and toteutuneet-pisteet luvatut-pisteet tarjouksen-tavoitehinta tavoitehinta)
    (let [erotus (- luvatut-pisteet toteutuneet-pisteet)
          tyyppi (cond
                   (= erotus 0) "taytetty"
                   (< erotus 0) "bonus"
                   (> erotus 0) "sanktio")
          lupaussanktio (when (= tyyppi "sanktio") (* 0.0018 tarjouksen-tavoitehinta erotus))
          lupausbonus (when (= tyyppi "bonus") (* 0.008 tarjouksen-tavoitehinta (* -1 erotus)))
          ;; Valitaan lupauspäätös, joissa tyyppi täsmää
          lupauspaatos (first (filter
                                (fn [paatos]
                                  (and
                                    (= (:nimi paatos) "Lupaukset")
                                    (= (:tyyppi paatos) tyyppi)))
                                paatokset))

          ;; Korvataan koneelta saatu päätös tässä valistellulta

          lupauspaatos (-> lupauspaatos
                           (assoc :tyyppi tyyppi)
                           (assoc :lupaussanktio lupaussanktio)
                           (assoc :lupausbonus lupausbonus)
                           (assoc :tavoitehinta tavoitehinta)
                           (assoc :tarjous_tavoitehinta tarjouksen-tavoitehinta)
                           (assoc :luvatut_pisteet luvatut-pisteet)
                           (assoc :toteutuneet_pisteet toteutuneet-pisteet))
          ;; Poista kaikki lupauspäätökset listasta
          paatokset (remove (fn [paatos] (= (:nimi paatos) "Lupaukset")) paatokset)
          ;; Ja lisää muokattu takaisin
          paatokset (sort-by :jarjestys (conj paatokset lupauspaatos))]
      paatokset)
    ;; Ehdot eivät täyttyneet, otetaan lupauspäätökset pois listasta ja lisätään virheilmoitus päätökselle
    (sort-by :jarjestys
             (conj
               (filter #(not= (:nimi %) "Lupaukset") paatokset)
               {:nimi "Lupaukset" :virhe "Toteutuneita pisteitä, luvattuja pisteitä tai tarjouksen tavoitehintaa ei ole määritelty." :jarjestys 1}))))

(defn valimistele-tavoitehinnan-muutospaatos [paatokset urakan-alkuvuosi tavoitehinta kattohinta kuluva-hoitovuosi]
  ;; Ota mukaan oikea tavoitehinnan muutospäätös, jos ehdot täyttyvät
  (if (and kattohinta tavoitehinta)
    (let [;; Versio 1a on käytössä urakoille, jotka ovat alkaneet 21 tai jälkeen. 1a:ssa kattohintaa ei syötetä käsin
          ;; versio 1b on käytössä 19-20 alkaville urakoille -- 1b:ssä kattohinta syötetään käsin.
          versio (if (and
                       (> urakan-alkuvuosi 2021)
                       (< urakan-alkuvuosi 2024)
                       (<= kuluva-hoitovuosi 2024)) "1a"
                                                    "1b")
          ;; Korvataan koneelta saatu päätös tässä valistellulta
          tavoitehinnan-muutospaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan muutokset") %) paatokset))
          tavoitehinnan-muutospaatos (-> tavoitehinnan-muutospaatos
                                         (assoc :versio versio)
                                         (assoc :tavoitehinta tavoitehinta)
                                         (assoc :kattohinta kattohinta))
          paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan muutokset")) paatokset)
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-muutospaatos))]
      paatokset)
    ;; Ehdot eivät täyttyneet, otetaan lupauspäätökset pois listasta ja lisätään virheilmoitus päätökselle
    (sort-by :jarjestys
             (conj
               (filter #(not= (:nimi %) "Lupaukset") paatokset)
               {:nimi "Lupaukset" :virhe "Toteutuneita pisteitä, luvattuja pisteitä tai tarjouksen tavoitehintaa ei ole määritelty." :jarjestys 1}))))

(defn valimistele-tavoitehinnan-alituspaatos [paatokset urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi tavoitehinta kustannukset]
  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  (if (and tavoitehinta kustannukset (> tavoitehinta kustannukset))
    (let [tavoitehinnan-alitus (- tavoitehinta kustannukset)
          ;; Poistetaan päätöskokneen tavoitehinna alituspäätös ja muokataan se alla
          tavoitehinnan-alituspaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan alitus")
                                                       %) paatokset))
          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Tavoitehinnan alitus"))
                      paatokset)

          ;; Jäljelle jäänyt paatos
          tavoitehinta-alitus-versio (if (< urakan-alkuvuosi 2025)
                                       "1"
                                       "2")
          tavh-3pros (* 0.03 tavoitehinta)
          ;; Versiossa 1 - Tavoitepalkkio on alituksesta 30%, mutta max 3% tavoitehinnasta - Mutta viimeisenä vuotena maksetaan kaikki eli 100% alituksesta
          tavoitepalkkio (if (= urakan-loppuvuosi kuluva-hoitovuosi)
                           tavoitehinnan-alitus             ;; Viimeisenä vuotena maksetaan kaikki. Muuten 30% tai max 3% , tai versiossa 2 maksetaan 75% alituksesta
                           (if (= tavoitehinta-alitus-versio "1")
                             (min tavh-3pros (* 0.3 tavoitehinnan-alitus))
                             (min tavh-3pros (* 0.75 tavoitehinnan-alitus))))
          ;; Jos alituksesta maksettava tavoitepalkkio on suurempi, kuin 3% tavoitehinnasta, siirretään ylittävä osuus seuraavan hoitovuden alennukseksi - Paitsi tietenkin viimeisenä vuotena
          siirron-maara (if (= urakan-loppuvuosi kuluva-hoitovuosi)
                          nil                               ;; Viimeisenä vuotena maksetaan kaikki. Eli ei siirretä mitään
                          (if (= tavoitehinta-alitus-versio "1")
                            (when (> (* 0.3 tavoitehinnan-alitus) tavh-3pros) (- (* 0.3 tavoitehinnan-alitus) (* 0.03 tavoitehinta)))
                            (when (> (* 0.75 tavoitehinnan-alitus) tavh-3pros) (- (* 0.75 tavoitehinnan-alitus) (* 0.03 tavoitehinta)))))

          tavoitehinnan-alituspaatos (-> tavoitehinnan-alituspaatos
                                         (assoc :versio tavoitehinta-alitus-versio)
                                         (assoc :tavoitehinta tavoitehinta)
                                         (assoc :toteutuneet_kustannukset kustannukset)
                                         (assoc :alituksen_maara tavoitehinnan-alitus)
                                         (assoc :siirron_maara siirron-maara)
                                         (assoc :tavoitepalkkio tavoitepalkkio))
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-alituspaatos))]

      paatokset)
    ;; Jos tarvittavia tietoja ei ole, niin poistetaan sekä tavoitehinnan alitus
    (remove
      (fn [paatos]
        (= (:nimi paatos) "Tavoitehinnan alitus"))
      paatokset)))

(defn valmistele-tavoitehinnan-ylityspaatos [paatokset urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi tavoitehinta kattohinta kustannukset mhu-tyyppi]
  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  (if (and kattohinta tavoitehinta kustannukset (> kustannukset tavoitehinta))
    (let [;; Ylitys + tavoitehinta ei voi ylittää kattohintaa. Eli maksettavat rahat on aina tavoitehinnan ja
          ;; kattohinnan väliin jääviä summia. Kattohinnan ylittävät summat menee aina urakoitsijan maksettavaksi
          tavoitehinnan-ylitys (min (- kustannukset tavoitehinta) (- kattohinta tavoitehinta))
          tavoitehinnan-ylityspaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan ylitys")
                                                       %) paatokset))
          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Tavoitehinnan ylitys"))
                      paatokset)

          ;; Jäljelle jäänyt paatos
          versio (cond
                   (and (< urakan-alkuvuosi 2025) (= "MHU" mhu-tyyppi)) "1"
                   (and (= urakan-alkuvuosi 2024) (= "MHU+" mhu-tyyppi)) "2"
                   (>= urakan-alkuvuosi 2025) "3")
          tilaajan-prosentti (cond
                               (= versio "1") 70
                               (= versio "2") 50
                               (= versio "3") 25)
          urakoitsijan-prosentti (- 100 tilaajan-prosentti)

          tavoitehinnan-ylityspaatos (-> tavoitehinnan-ylityspaatos
                                         (assoc :toteutuneet_kustannukset kustannukset)
                                         (assoc :versio versio)
                                         (assoc :tavoitehinta tavoitehinta)
                                         (assoc :toteutuneet_kustannukset kustannukset)
                                         (assoc :ylityksen_maara tavoitehinnan-ylitys)
                                         (assoc :tilaajan_prosentti tilaajan-prosentti)
                                         (assoc :urakoitsijan_prosentti urakoitsijan-prosentti)
                                         (assoc :tilaaja_maksaa (* (/ tilaajan-prosentti 100) tavoitehinnan-ylitys))
                                         (assoc :urakoitsija_maksaa (* (/ urakoitsijan-prosentti 100) tavoitehinnan-ylitys)))
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-ylityspaatos))]
      paatokset)
    ;; Jos tarvittavia tietoja ei ole, niin poistetaan tavoitehinnan ylitys
    (remove
      (fn [paatos]
        (= (:nimi paatos) "Tavoitehinnan ylitys"))
      paatokset)))

(defn valmistele-kattohinnan-paatokset [paatokset kattohinta kustannukset]
  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  (if (and kattohinta kustannukset (> kustannukset kattohinta))
    (let [kattohinnan-ylityspaatos (first (filter #(= (:nimi %) "Kattohinnan ylitys") paatokset))
          ylityksen-maara (- kustannukset kattohinta)
          ;; Täytetään pakolliset tiedot
          kattohinnan-ylityspaatos (-> kattohinnan-ylityspaatos
                                       (assoc :toteutuneet_kustannukset kustannukset)
                                       (assoc :kattohinta kattohinta)
                                       (assoc :ylityksen_maara ylityksen-maara)
                                       (assoc :urakoitsija_maksaa ylityksen-maara)
                                       (assoc :siirra? false) ;; Päätöksen pohjatietoja asetettaessa siirto on aina defaulttina false. Tietokannasta haettaessa tilanne voi olla eri.
                                       )

          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Kattohinnan ylitys"))
                      paatokset)
          paatokset (sort-by :jarjestys (conj paatokset kattohinnan-ylityspaatos))]
      paatokset)
    ;; Jos tarvittavia tietoja ei ole, niin poistetaan sekä tavoitehinna ylitys, että alitus
    (remove
      (fn [paatos]
        (= (:nimi paatos) "Kattohinnan ylitys"))
      paatokset)))

(defn nimi->avain [nimi]
  (keyword (str/lower-case (str/replace nimi #" " "-"))))
