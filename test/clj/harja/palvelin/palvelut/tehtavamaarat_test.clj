(ns harja.palvelin.palvelut.tehtavamaarat_test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tehtavamaarat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :tehtavamaarat (component/using
                                   (->Tehtavamaarat)
                                   [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(def paivitettavat-olemassaolevat-tehtavat
  [{:tehtava 1430 :maara 111}
   {:tehtava 1414 :maara 222}
   {:tehtava 1511 :maara 33.3}
   {:tehtava 1423 :maara 444}])

(def uudet-tehtavat
  [{:tehtava 1428 :maara 555}
   {:tehtava 1440 :maara 666}
   {:tehtava 1391 :maara 7.77}
   {:tehtava 1510 :maara 88.8}
   {:tehtava 1427 :maara 999}
   {:tehtava 1435 :maara 666}])


;; TODO: hae urkakkanumerot älä kovakoodaa

;; käyttää ennakkoon tallennettua testidataa
(deftest kaikki-tehtavamaarat-haettu-oikein
         (let [tehtavamaarat (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :tehtavamaarat  +kayttaja-jvh+ {:urakka-id 32
                                                                                    :hoitokauden-alkuvuosi 2020})
               tehtavamaarat-kannassa (ffirst (q
                                                     (str "SELECT *
                                                             FROM urakka_tehtavamaara
                                                            WHERE \"hoitokauden-aloitusvuosi\" = 2020 AND urakka = " 32)))]

           (is (= (count tehtavamaarat) (count tehtavamaarat-kannassa)) "Palutuneiden rivien lukumäärä vastaa kantaan tallennettuja.")
           (is (= (:maara (map [:tehtava 1511 :hoitokauden-alkuvuosi 2020]tehtavamaarat))  32.6) "Hoitokauden tehtävämäärä palautuu oikein.")))


;(deftest tallenna-tehtavamaarat-testi
;  (let [muutoshintaiset-tyot (kutsu-palvelua (:http-palvelin jarjestelma)
;                                             :muutoshintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja) @oulun-alueurakan-2005-2010-id)
;        muutoshintaisten-toiden-maara-ennen-paivitysta (ffirst (q
;                                                                 (str "SELECT count(*)
;                                                       FROM muutoshintainen_tyo
;                                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @oulun-alueurakan-2005-2010-id
;                                                                      ") AND alkupvm >= '2005-10-01' AND loppupvm <= '2012-09-30'")))
;
;        muokattavan-tyon-tehtava (ffirst (q (str "select id from toimenpidekoodi where nimi = 'I rampit'")))
;        muokattava-tyo (first (filter #(= (:tehtava %) muokattavan-tyon-tehtava ) muutoshintaiset-tyot))
;        uusi-yksikkohinta 888.0
;        uusi-yksikko "kg"
;        muokattava-tyo-uudet-arvot (assoc muokattava-tyo :yksikkohinta uusi-yksikkohinta :yksikko uusi-yksikko)
;
;        muutoshintaiset-tyot-paivitetty (kutsu-palvelua (:http-palvelin jarjestelma)
;                                                        :tallenna-muutoshintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja)
;                                                        {:urakka-id @oulun-alueurakan-2005-2010-id
;                                                         :tyot       [muokattava-tyo-uudet-arvot]})
;        muokattu-tyo-paivitetty (first (filter #(= (:tehtava %) muokattavan-tyon-tehtava) muutoshintaiset-tyot-paivitetty))
;
;        alkuperaiset-arvot-palautettu (kutsu-palvelua (:http-palvelin jarjestelma)
;                                                      :tallenna-muutoshintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja)
;                                                      {:urakka-id @oulun-alueurakan-2005-2010-id
;                                                       :tyot       [muokattava-tyo]})
;        muokattu-tyo-palautuksen-jalkeen   (first (filter #(= (:tehtava %) muokattavan-tyon-tehtava ) alkuperaiset-arvot-palautettu))]
;
;    (is (= (count muutoshintaiset-tyot)  muutoshintaisten-toiden-maara-ennen-paivitysta) "Tallennuksen jälkeen muutoshintaisten määrä")
;
;    (is (= (:yksikkohinta muokattu-tyo-paivitetty) uusi-yksikkohinta) "Tallennuksen jälkeen muutoshintaisen yksikköhinta")
;    (is (= (:yksikko muokattu-tyo-paivitetty) uusi-yksikko) "Tallennuksen jälkeen muutoshintaisen yksikkö")
;    (is (= (:yksikkohinta muokattu-tyo-palautuksen-jalkeen) 4.5) "Muutoshintaisen työn vanha tila palautettu, yksikköhinta")
;    (is (= (:yksikko muokattu-tyo-palautuksen-jalkeen) "tiekm") "Muutoshintaisen työn vanha tila palautettu, yksikkö")))
