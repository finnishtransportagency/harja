(ns harja.palvelin.raportointi.vastaanottotarkastus-mhu-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]

            [harja.palvelin.komponentit.tietokanta :as tietokanta]

            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.raportointi.raportit :as raportit]
            [harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu :as vastaanottotarkastus-mhu]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae-urakan-lupaustiedot (component/using
                                     (lupaus-palvelu/->Lupaus {:kehitysmoodi true})
                                     [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)

;; Meillä on kaksi vastaantottotarkastusraporttia, joista toinen on päällystysurakoille ja toinen MHU-urakoille.
;; Testataan, että ne ovat rekisteröityinä eri urakkatyyppien alle ja että ne eroavat toisistaan.
(deftest vastaanottotarkastusraportit-ovat-erilliset
  (let [paallystys (get raportit/raportit-nimen-mukaan :vastaanottotarkastusraportti)
        mhu (get raportit/raportit-nimen-mukaan :vastaanottotarkastusraportti-mhu)]
    (testing "molemmat raportit ovat rekisteröityinä"
      (is (some? paallystys))
      (is (some? mhu)))
    (testing "raportit on kohdistettu eri urakkatyypeille"
      (is (= #{:paallystys} (:urakkatyyppi paallystys)))
      (is (= #{:teiden-hoito} (:urakkatyyppi mhu))))
    (testing "MHU-raportti tarvitsee yhden urakan"
      (is (= #{"urakka"} (:konteksti mhu)))
      (is (= #{:teiden-hoito} (:urakkatyyppi mhu)))
      (is (= "Vastaanottotarkastusraportti - MHU" (:kuvaus mhu))))
    (testing "raporteilla on eri tunnisteet ja toteutukset"
      (is (not= (:nimi paallystys) (:nimi mhu)))
      (is (not= (:suorita paallystys) (:suorita mhu))))))

(deftest raportti-sisaltaa-lupaukset-hoitovuosittain
  (let [urakka-id-raasepori (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        hoitokaudet [{:alkupvm #inst "2021-10-01T00:00:00.000-00:00"
                      :loppupvm #inst "2022-09-30T23:59:59.000-00:00"}
                     {:alkupvm #inst "2022-10-01T00:00:00.000-00:00"
                      :loppupvm #inst "2023-09-30T23:59:59.000-00:00"}]
        lupaustiedot (fn [_ {:keys [valittu-hoitokausi]}]
                       (if (= (first valittu-hoitokausi) #inst "2021-10-01T00:00:00.000-00:00")
                         {:lupaus-sitoutuminen {:pisteet 70}
                          :yhteenveto {:pisteet {:toteuma 65}}}
                         {:lupaus-sitoutuminen {:pisteet 80}
                          :yhteenveto {:pisteet {:toteuma 75}}}))
        raportti (with-redefs [urakat-q/hae-urakka (fn [_ _] [{:nimi "Testiurakka"
                                                               :alkupvm #inst "2021-01-01T00:00:00.000-00:00"}])
                               urakat-q/hae-urakan-hoitokaudet (fn [_ _] hoitokaudet)
                               lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle lupaustiedot
                               valikatselmus-q/hae-bonukset (fn [_ {:keys [alkupvm]}]
                                                              (if (= alkupvm (-> hoitokaudet first :alkupvm))
                                                                [{:rahasumma 100M}]
                                                                [{:rahasumma 200M}]))
                               valikatselmus-q/hae-sanktiot (fn [_ {:keys [alkupvm]}]
                                                              (if (= alkupvm (-> hoitokaudet first :alkupvm))
                                                                [{:maara -25M}]
                                                                [{:maara -50M}]))]
                   (vastaanottotarkastus-mhu/suorita nil nil {:urakka-id urakka-id-raasepori}))]
    (is (= [:taulukko
            {:otsikko "Lupaukset" :sheet-nimi "Lupaukset" :tyhja nil}
            [{:otsikko "Hoitovuosi" :leveys 5}
             {:otsikko "Tarjouksen lupauspisteet" :leveys 5}
             {:otsikko "Toteutuneet lupauspisteet" :leveys 5}
             {:otsikko "Bonus/Sanktiot (€)" :leveys 5 :fmt :raha}]
            [["2021-2022" 70 65 75M]
             ["2022-2023" 80 75 150M]]]
          (nth raportti 2)))))

(deftest lupaukset-kayttaa-kuukausittaisia-pisteita-toimii
  (let [urakka-id-raasepori (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        urakan-tiedot (first (urakat-q/hae-urakka (:db jarjestelma) {:id urakka-id-raasepori}))
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet (:db jarjestelma) urakka-id-raasepori))
        raportti (with-redefs [lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle
                               (fn [_ _] {:lupaus-sitoutuminen {:pisteet 70}
                                          :yhteenveto {:pisteet {:maksimi 100, :ennuste 100, :toteuma 100}}})
                               valikatselmus-q/hae-bonukset (fn [_ _] [{:rahasumma 100M}])
                               valikatselmus-q/hae-sanktiot (fn [_ _] [{:maara -25M}])]
                   (vastaanottotarkastus-mhu/lupaukset-taulukko (:db jarjestelma) urakka-id-raasepori urakan-tiedot hoitokaudet))]
    (is (= ["2021-2022" 70 100 75M]
          (first (last raportti))))))


