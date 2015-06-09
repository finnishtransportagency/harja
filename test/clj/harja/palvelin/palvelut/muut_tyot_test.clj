(ns harja.palvelin.palvelut.muut-tyot-test
  (:require [clojure.test :refer :all]

            [harja.kyselyt.urakat :as urk-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.muut-tyot :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (apply tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :muutoshintaiset-tyot (component/using
                                   (->Muut-tyot)
                                   [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; käyttää testidata.sql:stä tietoa
(deftest kaikki-muut-tyot-haettu-oikein
         (let [muutoshintaiset-tyot (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :muutoshintaiset-tyot +kayttaja-tero+ @oulun-alueurakan-id)
               oulun-alueurakan-toiden-lkm (ffirst (q
                                                     (str "SELECT count(*)
                                                             FROM muutoshintainen_tyo
                                                            WHERE urakka = " @oulun-alueurakan-id)))
               ramppitehtavan-id (ffirst (q (str "SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='I rampit'")))
               ;; testidata.sta: {:loppupvm #inst "2010-09-29T21:00:00.000-00:00", :yksikko tiekm, :tehtava 1384,
               ;; :urakka 1, :yksikkohinta 4.5, :toimenpideinstanssi 1, :id 8,
               ;; :tehtavan_nimi I rampit, :sopimus 1, :alkupvm #inst "2005-09-30T21:00:00.000-00:00"
               ramppitehtava (first (filter #(= (:tehtava %) ramppitehtavan-id) muutoshintaiset-tyot))
               urakan-alkupvm (java.sql.Date. 105 9 1) ;;1.10.2005
               urakan-loppupvm (java.sql.Date. 110 8 30)] ;;30.9.2010
           (is (= (:yksikkohinta ramppitehtava) 4.5) "muutoshintaisen yksikköhinta")
           (is (= (:yksikko ramppitehtava) "tiekm") "muutoshintaisen yksikköhinta")
           (is (= (:tehtavan_nimi ramppitehtava) "I rampit") "muutoshintaisen tehtävän nimi")
           (is (= (:alkupvm  ramppitehtava) urakan-alkupvm) "muutoshintaisen tehtävän nimi")
           (is (= (:loppupvm ramppitehtava) urakan-loppupvm) "muutoshintaisen tehtävän nimi")
           (is (= (count muutoshintaiset-tyot) oulun-alueurakan-toiden-lkm) "muutoshintaisten lkm")))