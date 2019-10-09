(ns harja.palvelin.palvelut.budjettisuunnittelu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.budjettisuunnittelu :refer :all]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pois-kytketyt-ominaisuudet]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (luo-testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet (component/using
                                                      (pois-kytketyt-ominaisuudet/->PoisKytketytOminaisuudet #{})
                                                      [:http-palvelin])
                        :budjetoidut-tyot (component/using
                                            (->Budjettisuunnittelu)
                                            [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each jarjestelma-fixture)

:budjetoidut-tyot
:budjettitavoite
:tallenna-budjettitavoite
:tallenna-kiinteahintaiset-tyot
:tallenna-johto-ja-hallintokorvaukset
:tallenna-kustannusarvioitu-tyo

;; sampoa varten likaiseksi merkitseminen

(deftest budjetoidut-tyot-haku
  (let [urakka-id (:id (first (q-map "SELECT id FROM urakka WHERE nimi='Pellon MHU testiurakka (3. hoitovuosi)';")))
        budjetoidut-tyot (hae-urakan-budjetoidut-tyot (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id})]
    (testing "Kiinteähintaiset työt on oikein"
      (let [kiinteahintaiset-tyot-toimenpiteittain (group-by :toimenpide (:kiinteahintaiset-tyot budjetoidut-tyot))]
        (doseq [[toimenpide tehtavat] kiinteahintaiset-tyot-toimenpiteittain
                :let [tehtavat-vuosittain (group-by :vuosi tehtavat)]]
          (is (nil? (some (fn [{:keys [tehtava tehtavaryhma]}]
                            (when (or tehtava tehtavaryhma)
                              true))
                          tehtavat))
              "Kiinteähintainen tehtävä tulisi olla tallennettuna vain toimenpidetasolle")
          (is (nil? (some (fn [{:keys [summa]}]
                            (when-not (number? summa)
                              true))
                          tehtavat))
              "Kaikille summille pitäisi olla jokin arvo")
          (is (= 6 (count tehtavat-vuosittain)) (str "Tehtäviä pitäisi olla merkattuna kuudelle kalenterivuodelle. Toimenpiteelle "
                                                     toimenpide " löytyi " (count tehtavat-vuosittain) " vuodelle"))
          (is (= #{10 11 12}
                 (into #{}
                       (map :kuukausi
                            (val (first (sort-by key tehtavat-vuosittain))))))
              "Ensimmäisellä kalenterivuodella pitäisi olla arvoja vain kolmelle viimeiselle kuukaudelle")
          (is (= (into #{} (range 1 10))
                 (into #{}
                       (map :kuukausi
                            (val (last (sort-by key tehtavat-vuosittain))))))
              "Viimeisellä kalenterivuodella pitäisi olla arvoja vain tammikuusta syysykuuhun"))))))