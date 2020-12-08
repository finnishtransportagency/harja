(ns harja.palvelin.palvelut.muut-tyot-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.muut-tyot :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :muutoshintaiset-tyot (component/using
                                   (->Muut-tyot)
                                   [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; käyttää testidata.sql:stä tietoa
(deftest kaikki-muut-tyot-haettu-oikein
         (let [muutoshintaiset-tyot (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :muutoshintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja) @oulun-alueurakan-2005-2010-id)
               oulun-alueurakan-toiden-lkm (ffirst (q
                                                     (str "SELECT count(*)
                                                             FROM muutoshintainen_tyo
                                                            WHERE urakka = " @oulun-alueurakan-2005-2010-id)))
               ramppitehtavan-id (ffirst (q (str "SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='I rampit'")))
               ;; testidata.sta: {:loppupvm #inst "2010-09-29T21:00:00.000-00:00", :yksikko tiekm, :tehtava 1384,
               ;; :urakka 1, :yksikkohinta 4.5, :toimenpideinstanssi 1, :id 8,
               ;; :tehtavan_nimi I rampit, :sopimus 1, :alkupvm #inst "2005-09-30T21:00:00.000-00:00"
               ramppitehtava (first (filter #(= (:tehtava %) ramppitehtavan-id) muutoshintaiset-tyot))
               urakan-alkupvm (pvm/luo-pvm 2005 9 1) ;;1.10.2005
               urakan-loppupvm (pvm/luo-pvm 2012 8 30)] ;;30.9.2010
           (is (= (:yksikkohinta ramppitehtava) 4.5) "muutoshintaisen yksikköhinta")
           (is (= (:yksikko ramppitehtava) "tiekm") "muutoshintaisen yksikköhinta")
           (is (= (:tehtavanimi ramppitehtava) "I rampit") "muutoshintaisen tehtävän nimi")
           (is (= (:alkupvm  ramppitehtava) urakan-alkupvm) "muutoshintaisen tehtävän nimi")
           (is (= (:loppupvm ramppitehtava) urakan-loppupvm) "muutoshintaisen tehtävän nimi")
           (is (= (count muutoshintaiset-tyot) oulun-alueurakan-toiden-lkm) "muutoshintaisten lkm")))


(deftest tallenna-muutoshintaiset-tyot-testi
  (let [muutoshintaiset-tyot (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :muutoshintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja) @oulun-alueurakan-2005-2010-id)
        muutoshintaisten-toiden-maara-ennen-paivitysta (ffirst (q
                                       (str "SELECT count(*)
                                                       FROM muutoshintainen_tyo
                                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @oulun-alueurakan-2005-2010-id
                                            ") AND alkupvm >= '2005-10-01' AND loppupvm <= '2012-09-30'")))

        muokattavan-tyon-tehtava (ffirst (q (str "select id from toimenpidekoodi where nimi = 'I rampit'")))
        muokattava-tyo (first (filter #(= (:tehtava %) muokattavan-tyon-tehtava ) muutoshintaiset-tyot))
        uusi-yksikkohinta 888.0
        uusi-yksikko "kg"
        muokattava-tyo-uudet-arvot (assoc muokattava-tyo :yksikkohinta uusi-yksikkohinta :yksikko uusi-yksikko)

        muutoshintaiset-tyot-paivitetty (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-muutoshintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja)
                                                      {:urakka-id @oulun-alueurakan-2005-2010-id
                                                       :tyot       [muokattava-tyo-uudet-arvot]})
        muokattu-tyo-paivitetty (first (filter #(= (:tehtava %) muokattavan-tyon-tehtava) muutoshintaiset-tyot-paivitetty))

        alkuperaiset-arvot-palautettu (kutsu-palvelua (:http-palvelin jarjestelma)
                                                     :tallenna-muutoshintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja)
                                                     {:urakka-id @oulun-alueurakan-2005-2010-id
                                                      :tyot       [muokattava-tyo]})
        muokattu-tyo-palautuksen-jalkeen   (first (filter #(= (:tehtava %) muokattavan-tyon-tehtava ) alkuperaiset-arvot-palautettu))]

    (is (= (count muutoshintaiset-tyot)  muutoshintaisten-toiden-maara-ennen-paivitysta) "Tallennuksen jälkeen muutoshintaisten määrä")

    (is (= (:yksikkohinta muokattu-tyo-paivitetty) uusi-yksikkohinta) "Tallennuksen jälkeen muutoshintaisen yksikköhinta")
    (is (= (:yksikko muokattu-tyo-paivitetty) uusi-yksikko) "Tallennuksen jälkeen muutoshintaisen yksikkö")
    (is (= (:yksikkohinta muokattu-tyo-palautuksen-jalkeen) 4.5) "Muutoshintaisen työn vanha tila palautettu, yksikköhinta")
    (is (= (:yksikko muokattu-tyo-palautuksen-jalkeen) "tiekm") "Muutoshintaisen työn vanha tila palautettu, yksikkö")))
