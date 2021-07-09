(ns harja.palvelin.raportointi.kulut-tehtavaryhmittain-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi :as raportointi]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.string :as cstr]))

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
                      urakkatieto-fixture
                      jarjestelma-fixture))

(deftest kulut-tehtavaryhmittain-testi
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kulut-tehtavaryhmittain
                                 :konteksti  "urakka"
                                 :urakka-id  @oulun-maanteiden-hoitourakan-2019-2024-id
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2019 12 1))
                                              :loppupvm (c/to-date (t/local-date 2020 8 30))}})
        odotettu-vastaus [:raportti {:nimi "Kulut tehtäväryhmittäin"} [:taulukko {:viimeinen-rivi-yhteenveto? true, :otsikko "Kulut tehtäväryhmittäin ajalla 01.12.2019 - 30.08.2020"} [{:leveys 1, :otsikko "Tehtäväryhmä"} {:leveys 1, :fmt :raha, :otsikko "Hoitokauden alusta 01.10.2019-30.08.2020"} {:leveys 1, :fmt :raha, :otsikko "Jaksolla 01.12.2019-30.08.2020"}] [(list "Talvihoito (A)" 6601.94M 3300.40M) (list "Talvisuola (B1)" 0 0) (list  "KFo, NaFo (B2)" 0 0) (list "Hiekoitus (B3)" 0 0) (list "Liikennemerkit ja liikenteenohjauslaitteet (L)" 0 0) (list "Puhtaanapito (P)" 111.11M 0) (list "Vesakonraivaukset ja puun poisto (V)" 333.33M 0) (list "Nurmetukset ja muut vihertyöt (N)" 222.22M 0) (list "Kuivatusjärjestelmät (K)" 2222.22M 0) (list "Rummut, päällystetiet (R)" 0 0) (list "Rummut, soratiet (S)" 0 0) (list "Kaiteet, aidat ja kivetykset (U)" 0 0) (list "Kuumapäällyste (Y1)" 11001.94M 5500.40M) (list "Kylmäpäällyste (Y2)" 0 0) (list "Käsipaikkaus pikapaikkausmassalla (Y4)" 0 0) (list "Puhallus-SIP (Y5)" 0 0) (list "Saumojen juottaminen bitumilla (Y6)" 0 0) (list "Valu (Y7)" 0 0) (list "Siltapäällysteet (H)" 0 0) (list "Sorapientareet (O)" 0 0) (list "Sorastus (M)" 8801.94M 4400.40M) (list "Sillat ja laiturit (I)" 0 0) (list "Sorateiden hoito (C)" 0 0) (list "Kesäsuola, materiaali (D)" 0 0) (list "Äkilliset hoitotyöt, Soratiet (T1)" 0 0) (list "Vahinkojen korjaukset, Soratiet (T2)" 0 0) (list "Avo-ojitus, päällystetyt tiet (X)" 0 0) (list "Avo-ojitus, soratiet (Z)" 15401.94M 7700.40M) (list "RKR-korjaus (Q)" 13201.94M 6600.40M) (list "Muut (F)" 0 0) (list "ELY-rahoitteiset, liikenneympäristön hoito (E)" 0 0) (list "ELY-rahoitteiset, ylläpito (E)" 0 0) (list "Tilaajan rahavaraus (T3)" 0 0) (list "Hoidonjohtopalkkio (G)" 10.20M 10.20M) (list "Johto- ja hallintokorvaus (J)" 310.20M 260.20M) (list "Erillishankinnat (W)" 344.20M 294.20M) (list "Alataso Lisätyöt" 0 0) ["Yhteensä" 58563.18M 28066.60M]]] [:taulukko {:otsikko "Urakkavuoden alusta", :viimeinen-rivi-yhteenveto? true} [{:leveys 1, :otsikko ""} {:leveys 1, :otsikko "", :fmt :raha}] [["Tavoitehinta: " 250000M] ["Urakkavuoden alusta tav.hintaan kuuluvia: " 58563.18M]  ["Jäljellä: " 191436.82M]]]]

        vastaus-ulkopuolella (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kulut-tehtavaryhmittain
                                 :konteksti  "urakka"
                                 :urakka-id  @oulun-maanteiden-hoitourakan-2019-2024-id
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2014 12 1))
                                              :loppupvm (c/to-date (t/local-date 2015 8 30))}})
        yhteensa (some #(when (= "Yhteensä" (first %)) %)
                       (-> vastaus
                           (nth 2)
                           (nth 3)))
        eka-luku (second yhteensa)
        toka-luku (nth yhteensa 2)
        raportti-avainsana (first vastaus)
        taulukot (nth vastaus 2)
        taulukko-avainsana (first taulukot)
        taulukon-rivit (-> vastaus
                           (nth 2)
                           (nth 3))]
    (is (vector? vastaus) "Raportille palautuu tavaraa")
    (is (and (= :raportti raportti-avainsana)
             (= :taulukko taulukko-avainsana)
             (vector? taulukon-rivit)
             (> (count taulukon-rivit)
                0)) "Vastaus näyttää raportilta")
(is (= vastaus odotettu-vastaus))

    (is (and
          (> toka-luku 0)
          (> eka-luku 0)) "Raportille lasketaan summat oikein (jos testidata muuttuu, tää voi kosahtaa)")
    (is (every? #(let [eka (second %)
                       toka (nth % 2)]
                   (= 0 eka toka))
                (-> vastaus-ulkopuolella
                    (nth 2)
                    (nth 3))) "Raportille ei tule väärää tavaraa")))