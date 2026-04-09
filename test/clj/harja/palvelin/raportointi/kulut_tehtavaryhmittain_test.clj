(ns harja.palvelin.raportointi.kulut-tehtavaryhmittain-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi :as raportointi]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

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

(def odotettu-raportti
  [:raportti {:nimi "Kulut tehtäväryhmittäin", :rajoita-pdf-rivimaara nil}
   [:taulukko {:viimeinen-rivi-yhteenveto? true, :otsikko "Kulut tehtäväryhmittäin ajalla 01.12.2019 - 30.08.2020" :sheet-nimi "Kulut tehtäväryhmittäin"}
    [{:leveys 1, :otsikko "Tehtäväryhmä"} {:leveys 1, :fmt :raha, :otsikko "Hoitokauden alusta 01.10.2019-30.09.2020"} {:leveys 1, :fmt :raha, :otsikko "Jaksolla 01.12.2019-30.08.2020"}]
    [(list "A - Talvihoito" 6601.94M 3300.40M)
     (list "B1 - Talvisuola" 0 0)
     (list "B2 - KFo, NaFo" 0 0)
     (list "B3 - Hiekoitus" 0 0)
     (list "C - Sorateiden hoito" 8801.94M 4400.40M)
     (list "D - Kesäsuola, materiaali" 0 0)
     (list "E - ELY-rahoitteiset, liikenneympäristön hoito" 0 0)
     (list "E - ELY-rahoitteiset, ylläpito" 0 0)
     (list "F - Muut, liikenneympäristön hoito" 0 0)
     (list "F - Muut, MHU ylläpito" 1000.00M 0)
     (list "G - Hoidonjohtopalkkio" 110.20M 60.20M)
     (list "H - Siltapäällysteet" 0 0)
     (list "I - Sillat ja laiturit" 0 0)
     (list "J - Johto- ja hallintokorvaus" 10.20M 10.20M)
     (list "K - Kuivatusjärjestelmät" 2222.22M 0)
     (list "L - Liikennemerkit ja liikenteenohjauslaitteet" 0 0)
     (list "M - Liikenteen varmistaminen kelirikkokohteessa" 0 0)
     (list "M - Sorastus" 0 0)
     (list "N - Nurmetukset ja muut vihertyöt" 222.22M 0)
     (list "O - Sorapientareet" 0 0)
     (list "P - Puhtaanapito" 111.11M 0)
     (list "Q - RKR-korjaus" 13201.94M 6600.40M)
     (list "R - Rummut, päällystetiet" 0 0)
     (list "S - Rummut, soratiet" 0 0)
     (list "T1 - Äkilliset hoitotyöt, Talvihoito" 0 0)
     (list "T1 - Äkilliset hoitotyöt, Liikenneympäristön hoito" 4444.44M 0)
     (list "T1 - Äkilliset hoitotyöt, Soratiet" 0 0)
     (list "T2 - Vahinkojen korjaukset, Talvihoito" 0 0)
     (list "T2 - Vahinkojen korjaukset, Liikenneympäristön hoito" 0 0)
     (list "T2 - Vahinkojen korjaukset, Soratiet" 0 0)
     (list "T3 - Tilaajan rahavaraus" 0 0)
     (list "T4" 0 0)
     (list "U - Kaiteet, aidat ja kivetykset" 0 0)
     (list "V - Vesakonraivaukset ja puun poisto" 333.33M 0)
     (list "W - Erillishankinnat" 344.20M 294.20M)
     (list "X - Avo-ojitus, päällystetyt tiet" 0 0)
     (list "Y1 - Kuumapäällyste" 11001.94M 5500.40M)
     (list "Y2 - Kylmäpäällyste" 0 0)
     (list "Y3 - KT-Valu" 0 0)
     (list "Y4 - Käsipaikkaus pikapaikkausmassalla" 0 0)
     (list "Y5 - Puhallus-SIP" 0 0)
     (list "Y6 - Saumojen juottaminen bitumilla" 0 0)
     (list "Y7 - Valu" 0 0)
     (list "Y8 - Päällysteiden paikkaus, muut työt" 0 0)
     (list "Z - Avo-ojitus, soratiet" 15401.94M 7700.40M)
     ["Yhteensä" 63807.62M 27866.60M]]]
   [:taulukko {:otsikko "Urakkavuoden alusta", :viimeinen-rivi-yhteenveto? true}
    [{:leveys 1, :otsikko ""} {:leveys 1, :otsikko "", :fmt :raha}]
    [["Tavoitehinta: " 250000M]
     ["Urakkavuoden alusta tav.hintaan kuuluvia: " 63807.62M]
     ["Jäljellä: " 186192.38M]]]])

(deftest kulut-tehtavaryhmittain-testi
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kulut-tehtavaryhmittain
                                 :konteksti  "urakka"
                                 :urakka-id  @oulun-maanteiden-hoitourakan-2019-2024-id
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2019 12 1))
                                              :loppupvm (c/to-date (t/local-date 2020 8 30))}})
        odotettu-vastaus odotettu-raportti

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

(deftest kulut-tehtavaryhmittain-varmista-tehtavaryhman-voimassaolo-testi
  (let [;; Muokataan Siltapäällysteet (H) -tehtäväryhmän voimassaoloaikaa aiemmaksi kuin käytetyn urakan alkuvuosi, eli l-> 2018
        _ (u (str "UPDATE tehtavaryhma SET voimassaolo_loppuvuosi = '2018' WHERE nimi = 'H - Siltapäällysteet';"))
        taulukko (nth odotettu-raportti 2)
        raportin-tehtavaryhmat (nth taulukko 3)
        ;; Poista siltapäällysteet odotetusta raportista, koska sitä ei anneta, kun se ei ole voimassa
        tehtavaryhmat-ilman-siltapaallysteita (vec (remove #(= "H - Siltapäällysteet" (first %)) raportin-tehtavaryhmat))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :kulut-tehtavaryhmittain
                   :konteksti  "urakka"
                   :urakka-id  @oulun-maanteiden-hoitourakan-2019-2024-id
                   :parametrit {:alkupvm  (c/to-date (t/local-date 2019 12 1))
                                :loppupvm (c/to-date (t/local-date 2020 8 30))}})
        vastaus-tehtavaryhmat (nth (nth vastaus 2) 3)]
    (is (vector? vastaus) "Raportille palautuu tavaraa")
    (is (= vastaus-tehtavaryhmat tehtavaryhmat-ilman-siltapaallysteita))))
