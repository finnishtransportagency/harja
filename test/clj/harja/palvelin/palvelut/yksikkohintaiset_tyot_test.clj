(ns harja.palvelin.palvelut.yksikkohintaiset-tyot-test
  (:require [clojure.test :refer :all]

            [harja.kyselyt.urakat :as urk-q]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [harja.kyselyt.yksikkohintaiset-tyot :as yksikkohintaiset-q]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yksikkohintaiset-tyot :refer :all]
            [harja.testi :refer :all]
            [harja.domain.oikeudet :as oikeudet]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start 
                     (component/system-map
                      :db (tietokanta/luo-tietokanta testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :yksikkohintaiset-tyot (component/using
                                  (->Yksikkohintaiset-tyot false)
                                  [:http-palvelin :db])))))
  
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest generoi-vhu-yksikkohinta-kayttaa-yksikkokohtaisia-haarukoita
  (doseq [[yksikko [alaraja ylaraja]] {"h" [30 150]
                                       "tunti" [30 150]
                                       "km" [100 2000]
                                       "m" [1 200]
                                       "m2" [1 100]
                                       "m²" [1 100]
                                       "m3" [10 500]
                                       "m³" [10 500]
                                       "kpl" [10 1000]
                                       "kg" [1 50]
                                       "t" [25 500]
                                       "tn" [25 500]
                                       "ha" [50 1500]
                                       "vrk" [100 2000]
                                       "päivä" [100 2000]
                                       "tuntematon" [10 500]}]
    (is (<= alaraja (generoi-vhu-yksikkohinta yksikko) ylaraja)
      (str "Yksikön " yksikko " hinnan pitää olla haarukan sisällä"))))

(deftest generoi-vhu-yksikkohinnat-tarkistaa-kehitysmoodin-parametrit-ja-oikeudet
  (let [vhu-urakka-id (hae-urakan-id-nimella "Rentoselän urakka")
        hoitokausi [(pvm/vesivaylien-hoitokauden-alkupvm 2024)
                    (pvm/vesivaylien-hoitokauden-loppupvm 2025)]
        parametrit {:urakka-id vhu-urakka-id :valittu-hoitokausi hoitokausi}]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"vain kehitysympäristössä"
          (generoi-vhu-yksikkohinnat nil nil false parametrit)))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"Urakka ja hoitokausi ovat pakollisia"
          (generoi-vhu-yksikkohinnat nil nil true {:urakka-id vhu-urakka-id})))
    (is (thrown? Exception
          (generoi-vhu-yksikkohinnat (:db jarjestelma) {} true parametrit)))
    (with-redefs [oikeudet/vaadi-kirjoitusoikeus (fn [& _] true)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
            #"vain VHU-urakalle"
            (generoi-vhu-yksikkohinnat
              (:db jarjestelma)
              +kayttaja-jvh+
              true
              (assoc parametrit :urakka-id @oulun-alueurakan-2005-2010-id)))))))

(deftest generoi-vhu-yksikkohinnat-ohittaa-jo-hinnoitellut-tehtavat
  (let [db (:db jarjestelma)
        user +kayttaja-jvh+
        urakka-id (hae-urakan-id-nimella "Rentoselän urakka")
        sopimus-id (:id (first (sopimukset-q/hae-urakan-paasopimus db urakka-id)))
        hoitokausi [(pvm/vesivaylien-hoitokauden-alkupvm 2024)
                    (pvm/vesivaylien-hoitokauden-loppupvm 2025)]
        [alkupvm loppupvm] (map konversio/sql-date hoitokausi)
        hinnoittelemattomat (yksikkohintaiset-q/hae-vhu-urakan-hinnoittelemattomat-tehtavat
                              db {:urakkaid urakka-id
                                  :sopimusid sopimus-id
                                  :alkupvm alkupvm
                                  :loppupvm loppupvm})]
    (is (seq hinnoittelemattomat) "Testiurakalla on aluksi hinnoittelemattomia tehtäviä")
    (doseq [{:keys [tehtava tehtavan-yksikko yksikkohintaisen-tyon-yksikko]} hinnoittelemattomat]
      (yksikkohintaiset-q/lisaa-urakan-yksikkohintainen-tyo<!
        db {:maara 10M
            :yksikko (or yksikkohintaisen-tyon-yksikko tehtavan-yksikko)
            :yksikkohinta 12.3456M
            :arvioitu_kustannus 123.456M
            :kayttaja (:id user)
            :kuukausi nil
            :vuosi nil
            :urakka urakka-id
            :sopimus sopimus-id
            :tehtava tehtava
            :alkupvm alkupvm
            :loppupvm loppupvm}))
    (let [tallennettavat (atom nil)
          vastaus (with-redefs [tallenna-urakan-yksikkohintaiset-tyot
                                (fn [_ _ tiedot]
                                  (reset! tallennettavat (:tyot tiedot)))]
                    (generoi-vhu-yksikkohinnat db user true
                      {:urakka-id urakka-id :valittu-hoitokausi hoitokausi}))]
      (is (empty? (:tehtavat vastaus)))
      (is (nil? @tallennettavat) "Tallennusta ei kutsuta, kun kaikilla tehtävillä on jo hinta"))))

;; käyttää testidata.sql:stä tietoa
(deftest kaikki-yksikkohintaiset-tyot-haettu-oikein 
  (let [yksikkohintaiset-tyot (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :yksikkohintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja)
                                @oulun-alueurakan-2005-2010-id)
        oulun-alueurakan-toiden-lkm (ffirst (q 
                                             (str "SELECT count(*)
                                                       FROM yksikkohintainen_tyo
                                                      WHERE urakka = " @oulun-alueurakan-2005-2010-id)))]
    (is (= (count yksikkohintaiset-tyot) oulun-alueurakan-toiden-lkm))))


(deftest tallenna-yksikkohintaiset-tyot
  (let [tyot [{:yhteensa-kkt-1-9 122040, :yhteensa 162720, :yhteensa-kkt-10-12 40680,
                      :loppupvm (pvm/->pvm "31.12.2014") :yksikko "ha", :koskematon true,
                      :tehtava 1432, :urakka 4, :yksikkohinta 678, :maara 600,
                      :tehtavan_nimi "Metsän harvennus", :sopimus 2, :maara-kkt-1-9 180,
                      :alkupvm (pvm/->pvm "1.10.2014") :tehtavan_id 1432}
                     {:yhteensa-kkt-1-9 122040, :yhteensa 162720, :yhteensa-kkt-10-12 40680,
                      :loppupvm (pvm/->pvm "30.09.2015") :yksikko "ha", :koskematon true,
                      :tehtava 1432, :urakka 4, :yksikkohinta 678, :maara 1800,
                      :tehtavan_nimi "Metsän harvennus", :sopimus 2, :maara-kkt-1-9 180,
                      :alkupvm (pvm/->pvm "1.1.2015") :tehtavan_id 1432}]
        yksikkohintaiset-tyot-ennen (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :yksikkohintaiset-tyot +kayttaja-jvh+
                                              @oulun-alueurakan-2014-2019-id)
        paivitys (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-urakan-yksikkohintaiset-tyot +kayttaja-jvh+
                                 {:urakka-id @oulun-alueurakan-2014-2019-id
                                  :sopimusnumero @oulun-alueurakan-2014-2019-paasopimuksen-id
                                  :tyot tyot})
        yksikkohintaiset-tyot-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :yksikkohintaiset-tyot +kayttaja-jvh+
                                                      @oulun-alueurakan-2014-2019-id)
        metsan-harvennus-ennen-alku (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.10.2014"))
                                                         (= (:tehtavan_nimi %) "Metsän harvennus")) yksikkohintaiset-tyot-ennen))
        metsan-harvennus-ennen-loppu (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.1.2015"))
                                                          (= (:tehtavan_nimi %) "Metsän harvennus")) yksikkohintaiset-tyot-ennen))
        metsan-harvennus-jalkeen-alku (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.10.2014"))
                                                         (= (:tehtavan_nimi %) "Metsän harvennus")) yksikkohintaiset-tyot-jalkeen))
        metsan-harvennus-jalkeen-loppu (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.1.2015"))
                                                          (= (:tehtavan_nimi %) "Metsän harvennus")) yksikkohintaiset-tyot-jalkeen))
        paivityksen-jalkeen-alku (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.10.2014"))
                                                           (= (:tehtavan_nimi %) "Metsän harvennus")) paivitys))
        paivityksen-jalkeen-loppu (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.1.2015"))
                                                            (= (:tehtavan_nimi %) "Metsän harvennus")) paivitys))]
    (is (= 60.0 (:maara metsan-harvennus-ennen-alku)))
    (is (= 180.0 (:maara metsan-harvennus-ennen-loppu)))
    (is (= 600.0 (:maara metsan-harvennus-jalkeen-alku) (:maara paivityksen-jalkeen-alku)))
    (is (= 1800.0 (:maara metsan-harvennus-jalkeen-loppu) (:maara paivityksen-jalkeen-loppu)))
    (is (= 100.0 (:yksikkohinta metsan-harvennus-ennen-alku)))
    (is (= 100.0 (:yksikkohinta metsan-harvennus-ennen-loppu)))
    (is (= 678.0 (:yksikkohinta metsan-harvennus-jalkeen-alku) (:yksikkohinta paivityksen-jalkeen-alku)))
    (is (= 678.0 (:yksikkohinta metsan-harvennus-jalkeen-loppu) (:yksikkohinta paivityksen-jalkeen-loppu)))
    (is (= (count metsan-harvennus-ennen-loppu) (count paivityksen-jalkeen-loppu) (count metsan-harvennus-jalkeen-loppu)) "sama määrä eri yks.hint. palveluiden paluuarvioista")))
