(ns harja.palvelin.raportointi.sanktioraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [harja.palvelin.raportointi.excel :as excel]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit])
  (:import (org.apache.poi.xssf.usermodel XSSFWorkbook)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :pdf-vienti (component/using
                        (pdf-vienti/luo-pdf-vienti)
                        [:http-palvelin])
          :raportointi (component/using
                         (raportointi/luo-raportointi)
                         [:db :pdf-vienti])
          :raportit (component/using
                      (raportit/->Raportit)
                      [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn hae-urakan-aggregaatin-taulukot
  [raportti urakan-nimi]
  (filter #(and (vector? %)
             (= :taulukko (first %))
             (:aggregaatin-urakkataulukko? (second %))
             (= urakan-nimi (:sheet-nimi (second %))))
    (tree-seq coll? seq raportti)))

(defn hae-urakan-aggregaatin-taulukko
  [raportti urakan-nimi otsikko]
  (some #(when (= otsikko (get-in % [1 :otsikko])) %)
    (hae-urakan-aggregaatin-taulukot raportti urakan-nimi)))

(defn hae-taulukon-rivi
  [taulukko otsikko]
  (when taulukko
    (some (fn [rivi]
            (let [rivin-data (or (:rivi rivi) rivi)]
              (when (= otsikko (first rivin-data))
                rivin-data)))
      (apurit/taulukon-rivit taulukko))))

(defn sheet-tekstit
  [sheet]
  (mapcat (fn [rivi]
            (keep (fn [solu]
                    (when (= org.apache.poi.ss.usermodel.CellType/STRING (.getCellType solu))
                      (.getStringCellValue solu)))
              (iterator-seq (.cellIterator rivi))))
    (iterator-seq (.rowIterator sheet))))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :sanktioraportti
                   :konteksti  "urakka"
                   :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                   :parametrit {:alkupvm  (c/to-date (t/local-date 2011 10 1))
                                :loppupvm (c/to-date (t/local-date 2016 10 1))}})
        sanktiosumma (apurit/hae-yhteenveto-arvo vastaus "Sanktiot yhteensä")]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiosumma 25160M))
    (let [workbook (XSSFWorkbook.)]
      (excel/muodosta-excel vastaus workbook)
      (is (= 2 (.getNumberOfSheets workbook)))
      (is (= "Yhteenveto" (.getSheetName (.getSheetAt workbook 0))))
      (is (= "Oulun alueurakka 2014-2019"
             (.getSheetName (.getSheetAt workbook 1))))
      (let [yhteenveto-tekstit (sheet-tekstit (.getSheetAt workbook 0))
            erittely-tekstit (sheet-tekstit (.getSheetAt workbook 1))]
        (is (some #(= "Sanktiot yhteensä" %) yhteenveto-tekstit))
        (is (some #(= "Bonukset yhteensä" %) yhteenveto-tekstit))
        (is (some #(= "Arvovähennykset" %) yhteenveto-tekstit))
        (is (some #(= "Bonukset" %) erittely-tekstit))
        (is (some #(= "Arvonvähennykset" %) erittely-tekstit))
        (is (not-any? #(= "Yhteenveto" %) erittely-tekstit))))
    (let [otsikko "Sanktiot"
          taulukot (apurit/hae-osion-taulukot vastaus otsikko)
          taulukko (first taulukot)]
      (is (= "Oulun alueurakka 2014-2019" (:nimi (second vastaus))))
      (is (seq taulukot) "Sanktiot-osiota ei löytynyt")
      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:otsikko "Tyyppi"}
        {:otsikko "Sanktio (€)"}))))

(deftest raportin-suoritus-yllapidon-urakalle-erottelee-sakot-muistutukset-ja-bonukset
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :sanktioraportti
                   :konteksti  "urakka"
                   :urakka-id  5
                   :parametrit {:alkupvm      (c/to-date (t/local-date 2017 1 1))
                                :loppupvm     (c/to-date (t/local-date 2018 1 1))
                                :urakkatyyppi :paallystys}})
        sanktiot (apurit/hae-yhteenveto-arvo vastaus "Sakot yhteensä")
        bonukset (apurit/hae-yhteenveto-arvo vastaus "Bonukset yhteensä")
        muistutukset (apurit/hae-yhteenveto-arvo vastaus "Muistutukset")
        taulukko (some #(when (and (vector? %)
                                (= :taulukko (first %))
                                (= "Sakot ylläpitoluokittain" (get-in % [1 :otsikko])))
                          %)
                   (tree-seq coll? seq vastaus))]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiot -4500M))
    (is (=marginaalissa? bonukset 2000M))
    (is (= "2 kpl" muistutukset))
    (is (= ["Yhteensä" 5 -4500M]
           (hae-taulukon-rivi taulukko "Yhteensä")))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi               :sanktioraportti
                   :konteksti          "elinvoimakeskus"
                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                   :parametrit         {:alkupvm      (c/to-date (t/local-date 2011 10 1))
                                        :loppupvm     (c/to-date (t/local-date 2016 10 1))
                                        :urakkatyyppi :hoito}})
        sanktiosumma (apurit/hae-yhteenveto-arvo vastaus "Sanktiot yhteensä")]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiosumma 68320M))
    (is (seq (hae-urakan-aggregaatin-taulukot vastaus "Kajaanin alueurakka 2014-2019")))
    (is (seq (hae-urakan-aggregaatin-taulukot vastaus "Oulun alueurakka 2014-2019")))
    (let [otsikko "Sanktiot"
          taulukot (apurit/hae-osion-taulukot vastaus otsikko)
          taulukko (first taulukot)]
      (is (= "Pohjois-Suomi" (:nimi (second vastaus))))
      (is (seq taulukot) "Sanktiot-osiota ei löytynyt")
      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:otsikko "Tyyppi"}
        {:otsikko "Sanktio (€)"}))))

(deftest raportin-suoritus-hallintayksikolle-toimii-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi               :sanktioraportti
                   :konteksti          "elinvoimakeskus"
                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                   :parametrit         {:alkupvm      (c/to-date (t/local-date 2015 1 1))
                                        :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                        :urakkatyyppi :hoito}})
        sanktiosumma (apurit/hae-yhteenveto-arvo vastaus "Sanktiot yhteensä")
        bonussumma (apurit/hae-yhteenveto-arvo vastaus "Bonukset yhteensä")]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiosumma 9000M))
    (is (=marginaalissa? bonussumma 2000M))
    (let [otsikko "Sanktiot"
          taulukot (apurit/hae-osion-taulukot vastaus otsikko)
          taulukko (first taulukot)]
      (is (= "Pohjois-Suomi" (:nimi (second vastaus))))
      (is (seq taulukot) "Sanktiot-osiota ei löytynyt")
      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:otsikko "Tyyppi"}
        {:otsikko "Sanktio (€)"}))))

(deftest raportin-excel-elylle-sisaltaa-kaikki-aktiiviset-urakat
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi               :sanktioraportti
                   :konteksti          "elinvoimakeskus"
                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                   :parametrit         {:alkupvm      (c/to-date (t/local-date 2025 10 1))
                                        :loppupvm      (c/to-date (t/local-date 2026 9 30))
                                        :urakkatyyppi :hoito}})
        workbook (XSSFWorkbook.)]
    (excel/muodosta-excel vastaus workbook)
    (let [sheet-nimet (set (map #(.getSheetName %)
                             (map #(.getSheetAt workbook %)
                               (range (.getNumberOfSheets workbook)))))]
      (is (contains? sheet-nimet "POP MHU Kajaani 2025-2030"))
      (is (contains? sheet-nimet "POP MHU Suomussalmi 2024-2029"))
      (is (not (contains? sheet-nimet "Yhteenveto"))))))

(deftest raportin-mhu2025-sanktiot-ja-bonukset-kohdistuvat-oikein
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'POP MHU Kajaani 2025-2030'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :sanktioraportti
                   :konteksti  "urakka"
                   :urakka-id  urakka-id
                   :parametrit {:alkupvm      (c/to-date (t/local-date 2026 1 1))
                                :loppupvm     (c/to-date (t/local-date 2026 2 1))
                                :urakkatyyppi :hoito}})
        sanktio-taulukko (some #(when (= "A-ryhmä (tehtäväkohtainen sanktio)"
                                         (get-in % [1 :otsikko])) %)
                           (apurit/hae-osion-taulukot vastaus "Sanktiot"))
        bonus-taulukko (first (apurit/hae-osion-taulukot vastaus "Bonukset"))
        arvonvahennys-taulukko (first (apurit/hae-osion-taulukot vastaus "Arvonvähennykset"))]
    (is (=marginaalissa? (apurit/hae-yhteenveto-arvo vastaus "Sanktiot yhteensä") 1000M))
    (is (= ["Talvihoito, päätiet" 1000M]
           (hae-taulukon-rivi sanktio-taulukko "Talvihoito, päätiet")))
    (is (=marginaalissa? (apurit/hae-yhteenveto-arvo vastaus "Bonukset yhteensä") 1500M))
    (is (= ["Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" 1500M]
           (hae-taulukon-rivi bonus-taulukko
             "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta")))
    (is (=marginaalissa? (apurit/hae-yhteenveto-arvo vastaus "Arvovähennykset") 2500M))
    (is (= ["Arvonvähennys" 2500M]
           (hae-taulukon-rivi arvonvahennys-taulukko "Arvonvähennys")))))

(deftest raportin-mhu2026-noudattaa-t2-ja-whitelist-rajoja
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Sodankylän MHU 2026-2031'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :sanktioraportti
                   :konteksti  "urakka"
                   :urakka-id  urakka-id
                   :parametrit {:alkupvm      (c/to-date (t/local-date 2026 10 1))
                                :loppupvm     (c/to-date (t/local-date 2027 9 30))
                                :urakkatyyppi :hoito}})
        sanktio-taulukot (apurit/hae-osion-taulukot vastaus "Sanktiot")
        sanktio-taulukko (some #(when (= "A - Tehtäväkohtainen sanktio"
                                         (get-in % [1 :otsikko])) %)
                           sanktio-taulukot)
        bonus-taulukko (first (apurit/hae-osion-taulukot vastaus "Bonukset"))]
    (is (=marginaalissa? (apurit/hae-yhteenveto-arvo vastaus "Sanktiot yhteensä") 1800M))
    (is (= ["Talvihoito Ise/Is/L" 1800M]
           (hae-taulukon-rivi sanktio-taulukko "Talvihoito Ise/Is/L")))
    (is (=marginaalissa? (apurit/hae-yhteenveto-arvo vastaus "Bonukset yhteensä") 3600M))
    (is (= ["Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" 1100M]
           (hae-taulukon-rivi bonus-taulukko
             "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta")))
    (is (= ["Bonus alihankkijatyytyväisyyden kyselytutkimuksen tuloksesta" 1200M]
           (hae-taulukon-rivi bonus-taulukko
             "Bonus alihankkijatyytyväisyyden kyselytutkimuksen tuloksesta")))
    (is (= ["Bonus määräaikaan tehtävien töiden aiemmasta toteutuksesta" 1300M]
           (hae-taulukon-rivi bonus-taulukko
             "Bonus määräaikaan tehtävien töiden aiemmasta toteutuksesta")))
    (is (= ["Bonus liikennevahinkojen aiheuttajien selvittämisestä" 0]
           (hae-taulukon-rivi bonus-taulukko
             "Bonus liikennevahinkojen aiheuttajien selvittämisestä")))))

(deftest raportin-mhu2026-whitelistin-sallima-bonus-nakyy
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Nummi 26 - whitelistin testi'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :sanktioraportti
                   :konteksti  "urakka"
                   :urakka-id  urakka-id
                   :parametrit {:alkupvm      (c/to-date (t/local-date 2026 10 1))
                                :loppupvm     (c/to-date (t/local-date 2027 9 30))
                                :urakkatyyppi :hoito}})
        bonus-taulukko (first (apurit/hae-osion-taulukot vastaus "Bonukset"))]
    (is (=marginaalissa? (apurit/hae-yhteenveto-arvo vastaus "Bonukset yhteensä") 1400M))
    (is (= ["Bonus liikennevahinkojen aiheuttajien selvittämisestä" 1400M]
           (hae-taulukon-rivi bonus-taulukko
             "Bonus liikennevahinkojen aiheuttajien selvittämisestä")))))


(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :sanktioraportti
                   :konteksti  "koko maa"
                   :parametrit {:alkupvm      (c/to-date (t/local-date 2015 1 1))
                                :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                :urakkatyyppi :hoito}})
        sanktiosumma (apurit/hae-yhteenveto-arvo vastaus "Sanktiot yhteensä")
        bonussumma (apurit/hae-yhteenveto-arvo vastaus "Bonukset yhteensä")
        arvonvahennyssumma (apurit/hae-yhteenveto-arvo vastaus "Arvovähennykset")]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiosumma 17000M))
    (is (=marginaalissa? bonussumma 4000M))
    (is (=marginaalissa? arvonvahennyssumma 1000M))
    (is (seq (hae-urakan-aggregaatin-taulukot vastaus "Kajaanin alueurakka 2014-2019")))
    (is (seq (hae-urakan-aggregaatin-taulukot vastaus "Oulun alueurakka 2014-2019")))
    (let [workbook (XSSFWorkbook.)]
      (excel/muodosta-excel vastaus workbook)
      (is (>= (.getNumberOfSheets workbook) 2))
      (is (not-any? #(= "Yhteenveto" (.getSheetName %))
            (map #(.getSheetAt workbook %) (range (.getNumberOfSheets workbook)))))
      (is (some #(.contains (.getSheetName %) "Kajaanin alueurakka")
            (map #(.getSheetAt workbook %) (range (.getNumberOfSheets workbook)))))
      (is (some #(.contains (.getSheetName %) "Oulun alueurakka")
            (map #(.getSheetAt workbook %) (range (.getNumberOfSheets workbook)))))
      (is (not-any? #(.startsWith (.getSheetName %) "PSU ")
            (map #(.getSheetAt workbook %) (range (.getNumberOfSheets workbook)))))
      (doseq [sheet (map #(.getSheetAt workbook %) (range (.getNumberOfSheets workbook)))]
        (let [tekstit (sheet-tekstit sheet)]
          (is (some #(.contains % "Sanktiot") tekstit))
          (is (some #(.contains % "Bonukset") tekstit))
          (is (some #(.contains % "Arvonvähennykset") tekstit))
          (is (not-any? #(= "Yhteenveto" %) tekstit))
          (is (not-any? #(= "Sanktiot yhteensä" %) tekstit)))))
    (let [otsikko "Sanktiot"
          taulukot (apurit/hae-osion-taulukot vastaus otsikko)
          taulukko (first taulukot)]
      (is (= "Koko maa" (:nimi (second vastaus))))
      (is (seq taulukot) "Sanktiot-osiota ei löytynyt")
      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:otsikko "Tyyppi"}
        {:otsikko "Sanktio (€)"}))))

(defn suorita-sanktioraportti
  [konteksti [alkuvuosi alkukk alkupv] [loppuvuosi loppukk loppupv]]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :suorita-raportti
    +kayttaja-jvh+
    (merge
      {:nimi       :sanktioraportti
       :konteksti  konteksti
       :parametrit {:alkupvm  (c/to-date (t/local-date alkuvuosi alkukk alkupv))
                    :loppupvm (c/to-date (t/local-date loppuvuosi loppukk loppupv))}}
      (cond
        (= konteksti "urakka")
        {:urakka-id  (hae-oulun-alueurakan-2014-2019-id)}

        (= konteksti "elinvoimakeskus")
        {:elinvoimakeskus-id (hae-pohjois-suomen-evk-id)}))))

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-toimii-urakalle
  (let [urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella
        (suorita-sanktioraportti "urakka" [2018 10 1] [2019 9 30])
        taulukot (apurit/hae-osion-taulukot urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella "Sanktiot")
        taulukko (first taulukot)
        sanktiosumma (apurit/hae-yhteenveto-arvo
                       urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella
                       "Sanktiot yhteensä")]
    (is (= "Oulun alueurakka 2014-2019" (:nimi (second urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella))))
    (is (seq taulukot) "Sanktiot-osiota ei löytynyt")
    (is (=marginaalissa? sanktiosumma 777M))
    (is (= "Sanktio (€)" (-> taulukko (nth 2) second :otsikko)))))

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-laskee-sanktiot-vain-jos-viimeinen-kuukausi-on-mukana
  (let [urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella
        (suorita-sanktioraportti "urakka" [2018 9 1] [2019 8 1])
        taulukot (apurit/hae-osion-taulukot urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella "Sanktiot")
        taulukko (first taulukot)
        sanktiosumma (apurit/hae-yhteenveto-arvo
                       urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella
                       "Sanktiot yhteensä")]
    (is (= "Oulun alueurakka 2014-2019" (:nimi (second urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella))))
    (is (seq taulukot) "Sanktiot-osiota ei löytynyt")
    (is (=marginaalissa? sanktiosumma 0M))
    (is (= "Sanktio (€)" (-> taulukko (nth 2) second :otsikko)))))

(defn tarkista-ely-rivit
  [tarkistus-fn]
  (fn [rivi]
    (if (and (vector? rivi)
          (= (first rivi) "Ryhmä C, sakot yht."))
      (tarkistus-fn rivi)
      true)))

(defn sanktio-loytyy-elyriveissa
  [rivi]
  (= 777M (some
            (fn [arvo]
              (when-not (zero? arvo)
                arvo))
            (filter number? rivi))))

(defn ei-sanktiota-elyriveissa
  [rivi]
  (every? zero?
    (filter number? rivi)))

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-toimii-elylle
  (let [elylla-sanktiot-tulee-mukaan-jos-jossain-urakassa-viimeinen-hoitokausi
        (suorita-sanktioraportti "elinvoimakeskus" [2018 10 1] [2019 9 30])
        raportti elylla-sanktiot-tulee-mukaan-jos-jossain-urakassa-viimeinen-hoitokausi
        tunnistamattomat (hae-urakan-aggregaatin-taulukko
                           raportti
                           "Oulun alueurakka 2014-2019"
                           "Tunnistamattomat sanktiot")
        tunnistamaton-rivi (hae-taulukon-rivi tunnistamattomat "Määräpäivän ylitys")]
    (is (= "Pohjois-Suomi" (:nimi (second elylla-sanktiot-tulee-mukaan-jos-jossain-urakassa-viimeinen-hoitokausi))))
    (is (= ["Määräpäivän ylitys" 777M] tunnistamaton-rivi))))

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-toimii
  (let [elylla-sanktiot-ei-tule-mukaan-jos-edellista-casea-seuraava-hoitokausi
        (suorita-sanktioraportti "elinvoimakeskus" [2019 10 1] [2020 9 30])
        raportti elylla-sanktiot-ei-tule-mukaan-jos-edellista-casea-seuraava-hoitokausi
        vanhan-urakan-taulukot (hae-urakan-aggregaatin-taulukot
                                 raportti
                                 "Oulun alueurakka 2014-2019")]
    (is (= "Pohjois-Suomi" (:nimi (second elylla-sanktiot-ei-tule-mukaan-jos-edellista-casea-seuraava-hoitokausi))))
    (is (empty? vanhan-urakan-taulukot))))
