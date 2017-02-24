(ns harja.palvelin.palvelut.yllapito-toteumat-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.tiemerkinta-toteumat :as tt]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.spec.gen :as gen]
            [clojure.spec :as s]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :yllapitototeumat (component/using
                                            (->YllapitoToteumat)
                                            [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest yllapitototeumien-haku-test
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        alkupvm (pvm/luo-pvm 2016 0 1)
        loppupvm (pvm/luo-pvm 2016 11 31)
        res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :hae-yllapito-toteumat +kayttaja-jvh+
                            {:urakka urakka-id
                             :sopimus sopimus-id
                             :alkupvm alkupvm
                             :loppupvm loppupvm})
        oulun-tiemerkintaurakan-toiden-lkm (ffirst (q
                                                     (str "SELECT count(*)
                                                       FROM yllapito_toteuma
                                                     WHERE urakka = " urakka-id
                                                          " AND sopimus = " sopimus-id
                                                          " AND pvm >= '2016-1-01' AND pvm <= '2016-12-31'")))]
    (is (= (count res) oulun-tiemerkintaurakan-toiden-lkm) "Muiden töiden määrä")))

(deftest tallenna-yllapito-toteuma-test
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        alkupvm (pvm/luo-pvm 2016 0 1)
        loppupvm (pvm/luo-pvm 2016 11 31)
        toteuman-pvm (pvm/luo-pvm 2016 11 24)
        hyotykuorma {:pvm toteuman-pvm
                     :selite "Jouluaattona hommissa"
                     :laskentakohde [nil "ei kohdetta tässä.."]
                     :uusi-laskentakohde "Uusikohde"
                     :urakka urakka-id
                     :sopimus sopimus-id
                     :yllapitoluokka 1
                     :alkupvm alkupvm
                     :loppupvm loppupvm
                     :hinta 665.5}
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*)
                                                       FROM yllapito_toteuma
                                                     WHERE urakka = " urakka-id
                                            " AND sopimus = " sopimus-id
                                            " AND pvm >= '2016-1-01' AND pvm <= '2016-12-31'")))
        laskentakohdelkm-ennen (ffirst (q
                                         (str "SELECT count(*)
                                                       FROM urakka_laskentakohde
                                                     WHERE urakka = " urakka-id)))
        res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-yllapito-toteuma +kayttaja-jvh+
                            hyotykuorma)
        maara-lisayksen-jalkeen (ffirst (q
                                          (str "SELECT count(*)
                                                       FROM yllapito_toteuma
                                                     WHERE urakka = " urakka-id
                                               " AND sopimus = " sopimus-id
                                               " AND pvm >= '2016-1-01' AND pvm <= '2016-12-31'")))
        lisatty-toteuma (first (filter #(= (:pvm %) toteuman-pvm) (:toteumat res)))
        laskentakohteet-jalkeen (:laskentakohteet res)]
    (is (= (+ 1 maara-ennen-lisaysta) maara-lisayksen-jalkeen))
    (is (= (:pvm lisatty-toteuma) toteuman-pvm) "Tallennetun yllapitototeuman pvm")
    (is (= (:hinta lisatty-toteuma) 665.5M) "Tallennetun yllapitototeuman pvm")
    (is (= (count (:toteumat res)) maara-lisayksen-jalkeen (+ 1 maara-ennen-lisaysta)) "Tallennuksen jälkeen erilliskustannusten määrä")
    (is (= (count laskentakohteet-jalkeen) (+ 1 laskentakohdelkm-ennen)))))

(deftest hae-tiemerkinnan-yksikkohintaiset-tyot-toimii
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tiemerkinnan-yksikkohintaiset-tyot +kayttaja-jvh+
                                {:urakka-id urakka-id})]
    (is (= (count vastaus)) 3)))

(deftest usean-tiemerkinnan-yks-hint-toteuman-tallennus-toimii
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        yllapitokohde-id (hae-tiemerkintaurakkaan-osoitettu-yllapitokohde urakka-id)
        toteumien-maara 10
        pyynto {:urakka-id urakka-id
                :toteumat (->> (map (fn [_] (gen/generate (s/gen ::tt/tiemerkinnan-yksikkohintainen-tyo)))
                                    (range 0 toteumien-maara))
                               ;; Liitä osa toteumista ylläpitokohteeseen
                               (map #(assoc % :id nil
                                              :yllapitokohde-id (get [yllapitokohde-id nil] (int (rand 2))))))}
        maara-ennen-lisaysta (ffirst (q "SELECT COUNT(*) FROM tiemerkinnan_yksikkohintainen_toteuma;"))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-tiemerkinnan-yksikkohintaiset-tyot +kayttaja-jvh+
                                pyynto)
        maara-lisayksen-jalkeen (ffirst (q "SELECT COUNT(*) FROM tiemerkinnan_yksikkohintainen_toteuma;"))]
    (is (= (+ maara-ennen-lisaysta toteumien-maara) maara-lisayksen-jalkeen))))

(deftest tiemerkinnan-yks-hint-toteuma-kirjataan-oikein
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        testien-maara 100
        maara-ennen-testia (ffirst (q "SELECT COUNT(*) FROM tiemerkinnan_yksikkohintainen_toteuma;"))
        yllapitokohde-id (hae-tiemerkintaurakkaan-osoitettu-yllapitokohde urakka-id)]

    (loop [index 0]
      (let [pyynto {:urakka-id urakka-id
                    :toteumat [(-> (gen/generate (s/gen ::tt/tiemerkinnan-yksikkohintainen-tyo))
                                   ;; Tee tästä uusi toteuma ja liitä ylläpitokohde randomisti
                                   (assoc :id nil
                                          :yllapitokohde-id (get [yllapitokohde-id nil] (int (rand 2)))))]}

            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-tiemerkinnan-yksikkohintaiset-tyot +kayttaja-jvh+
                                    pyynto)]
        (when (< index testien-maara)
          (recur (inc index)))))

    (let [maara-testin-jalkeen (ffirst (q "SELECT COUNT(*) FROM tiemerkinnan_yksikkohintainen_toteuma;"))]
      (is (= (+ maara-ennen-testia testien-maara) maara-testin-jalkeen)))))
