(ns harja.palvelin.raportointi.tehtavamaarat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.raportointi.raportit.tehtavamaarat :as tm-r]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [clj-time.core :as t]
            [clj-time.coerce :as c])
  (:import (java.math RoundingMode)))

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

(def mock-rivit
  [{:toimenpide         "Toimenpide1"
    :nimi               "Tehtava1"
    :suunniteltu        (bigdec 1)
    :toteuma            (bigdec 1)
    :hallintayksikko    12
    :jarjestys          1
    :yksikko            "kg"
    :suunnitteluyksikko "kg"
    :toimenpide-jarjestys 1}
   {:toimenpide         "Toimenpide1"
    :nimi               "Tehtava2"
    :suunniteltu        (bigdec 1)
    :toteuma            (bigdec 1)
    :hallintayksikko    12
    :jarjestys          2
    :yksikko            "kg"
    :suunnitteluyksikko "kg"
    :toimenpide-jarjestys 1}
   {:toimenpide         "Toimenpide2"
    :nimi               "Tehtava3"
    :suunniteltu        (bigdec 1)
    :toteuma            (bigdec 1)
    :hallintayksikko    12
    :jarjestys          1
    :yksikko            "kg"
    :suunnitteluyksikko "kg"
    :toimenpide-jarjestys 2}
   {:toimenpide         "Toimenpide2"
    :nimi               "Tehtava4"
    :suunniteltu        (bigdec 1)
    :toteuma            (bigdec 1)
    :hallintayksikko    12
    :jarjestys          2
    :yksikko            "mm"
    :suunnitteluyksikko "kg"
    :toimenpide-jarjestys 2}
   {:toimenpide         "Toimenpide3"
    :nimi               "Tehtava5"
    :suunniteltu        (bigdec 1)
    :toteuma            (bigdec 1)
    :hallintayksikko    12
    :jarjestys          1
    :yksikko            "kg"
    :suunnitteluyksikko "kg"
    :toimenpide-jarjestys 3}])

(def pitais-tulla
  [{:rivi ["Pohjois-Pohjanmaa" "" "" "" ""] :korosta? true :lihavoi? true}
   {:rivi ["Toimenpide1" "" "" "" ""] :korosta-hennosti? true :lihavoi? true}
   ["Tehtava1" "kg" (bigdec 1) (bigdec 1) (* (.divide (bigdec 1) (bigdec 1) 4 RoundingMode/HALF_UP) 100)]
   ["Tehtava2" "kg" (bigdec 1) (bigdec 1) (* (.divide (bigdec 1) (bigdec 1) 4 RoundingMode/HALF_UP) 100)]
   {:rivi ["Toimenpide2" "" "" "" ""] :korosta-hennosti? true :lihavoi? true}
   ["Tehtava3" "kg" (bigdec 1) (bigdec 1) (* (.divide (bigdec 1) (bigdec 1) 4 RoundingMode/HALF_UP) 100)]
   ["Tehtava4" "mm" 0 (bigdec 1) "!"]
   {:rivi ["Toimenpide3" "" "" "" ""] :korosta-hennosti? true :lihavoi? true}
   ["Tehtava5" "kg" (bigdec 1) (bigdec 1) (* (.divide (bigdec 1) (bigdec 1) 4 RoundingMode/HALF_UP) 100)]])

(deftest tehtavamaarien-riviprosessointiharvelien-testit
  (testing "Rivien yhdistelyhärveli yhdistää saman tehtävän ja hallintayksikön sisältävät rivit"
    (let [rivit [{:nimi "Erillinen1" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
                 {:nimi "Erillinen2" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
                 {:nimi "Erillinen3" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
                 {:nimi "Erillinen4" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
                 {:nimi "Sama1" :hallintayksikko 1 :suunniteltu 4 :toteuma 2}
                 {:nimi "Sama1" :hallintayksikko 1 :suunniteltu 4 :toteuma 2}
                 {:nimi "Erillinen5" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
                 {:nimi "Erillinen6" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
                 {:nimi "Sama1" :hallintayksikko 2 :suunniteltu 1 :toteuma 1}
                 {:nimi "Sama2" :hallintayksikko 1 :suunniteltu 6 :toteuma 3}
                 {:nimi "Sama2" :hallintayksikko 1 :suunniteltu 6 :toteuma 3}]
          kombotetut (tm-r/kombota-samat-tehtavat rivit)
          taulukko (tm-r/muodosta-taulukko (:db jarjestelma)
                                           +kayttaja-jvh+
                                           (fn [_ _]
                                             mock-rivit)
                                           {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                            :alkupvm   (c/to-date (t/local-date 2020 10 1))
                                            :loppupvm  (c/to-date (t/local-date 2021 10 1))})]
      (is (= 9 (count kombotetut))
          "Yhdistetään samalla nimellä ja h.yksiköllä olevat rivit")
      (is (and
            (= 8 (:suunniteltu (some #(when (and
                                              (= (:hallintayksikko %) 1)
                                              (= (:nimi %) "Sama1")) %) kombotetut)))
            (= 4 (:toteuma (some #(when (and
                                          (= (:hallintayksikko %) 1)
                                          (= (:nimi %) "Sama1")) %) kombotetut)))
            (= 12 (:suunniteltu (some #(when (= (:nimi %) "Sama2") %) kombotetut)))
            (= 6 (:toteuma (some #(when (= (:nimi %) "Sama2") %) kombotetut))))
          "Onko yhdistetyt rivit laskettu oikein?")
      (is (= pitais-tulla
             (:rivit taulukko))
          "Tuleeko taulukko"))))

(deftest tehtavamaara-raportti-perusjutut
  (testing "Määrien haku raportille"
    (let [[urakka-raportti-tagi _
           [urakka-taulukko-tagi _ _ urakka-haun-rivit] :as urakan-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi       :tehtavamaarat
                           :konteksti  "urakka"
                           :urakka-id  (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :parametrit {:alkupvm  (c/to-date (t/local-date 2020 10 1))
                                        :loppupvm (c/to-date (t/local-date 2021 10 1))}})
          [_ _ [_ _ _ tyhja-aikavali-rivit] :as ei-tietoja-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi       :tehtavamaarat
                           :konteksti  "urakka"
                           :urakka-id  (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :parametrit {:alkupvm  (c/to-date (t/local-date 2014 10 1))
                                        :loppupvm (c/to-date (t/local-date 2015 10 1))}})
          [koko-raportti _ [koko-taulukko _ _ koko-maa-rivit] :as koko-maa-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi       :tehtavamaarat
                           :konteksti  "koko maa"
                           :parametrit {:alkupvm  (c/to-date (t/local-date 2020 10 1))
                                        :loppupvm (c/to-date (t/local-date 2021 10 1))}})
          [ely-raportti _ [ely-taulukko _ _ ely-rivit] :as ely-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi               :tehtavamaarat
                           :konteksti          "hallintayksikko"
                           :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                           :parametrit         {:alkupvm  (c/to-date (t/local-date 2020 10 1))
                                                :loppupvm (c/to-date (t/local-date 2021 10 1))}})]
      (testing "Urakka"
        (is (not (empty? urakka-haun-rivit)) "Palautuu raportille asioita urakalla")
        (is (empty? tyhja-aikavali-rivit) "Väärä aikaväli ei palauta rivejä urakalla")
        (is (contains? (first urakka-haun-rivit) :rivi) "Oikeat haut palauttuvat kivoja juttuja"))
      (is (not (empty? koko-maa-rivit)) "Palautuu raportille asioita koko maalla")
      (is (not (empty? ely-rivit)) "Palautuu raportille asioita hankintayksiköllä")
      (is (every? keyword? (conj []
                                 urakka-raportti-tagi
                                 urakka-taulukko-tagi
                                 koko-raportti
                                 koko-taulukko
                                 ely-taulukko
                                 ely-raportti)) "Palautuneet asiat näyttävät raportilta"))))

#_(deftest tehtavamaara-raportti-ikavat-jutut
    (testing "Määrien haku raportilla ei toimi"
      (let [[_ _ [_ _ _ rivit]]
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :suorita-raportti
                            +kayttaja-jvh+
                            {:nimi       :tehtavamaarat
                             :konteksti  "urakka"
                             :parametrit {:alkupvm  (c/to-date (t/local-date 2016 10 1))
                                          :loppupvm (c/to-date (t/local-date 2017 10 1))}})
            [_ _ [_ _ _ rivit2]]
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :suorita-raportti
                            +kayttaja-jvh+
                            {:nimi       :tehtavamaarat
                             :konteksti  "urakka"
                             :urakka-id  82347592
                             :parametrit {:alkupvm  (c/to-date (t/local-date 2016 10 1))
                                          :loppupvm (c/to-date (t/local-date 2017 10 1))}})]
        (is (empty? rivit) "Haetaan urakkaa ilman urakka-id")
        (is (thrown? IllegalArgumentException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                              :suorita-raportti
                                                              +kayttaja-jvh+
                                                              {:nimi       :tehtavamaarat
                                                               :konteksti  "urakka"
                                                               :urakka-id  8234759283495
                                                               :parametrit {:loppupvm (c/to-date (t/local-date 2017 10 1))}})) "Parametreja puuttuu")
        (is (empty? rivit2) "Ei olemassa oleva id"))))