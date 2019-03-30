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
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [clojure.string :as clj-str]))

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
                                                       FROM yllapito_muu_toteuma
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
        hyotykuorma {:urakka-id urakka-id
                     :sopimus-id sopimus-id
                     :alkupvm alkupvm
                     :loppupvm loppupvm
                     :toteumat [{:pvm toteuman-pvm
                                 :selite "Jouluaattona hommissa"
                                 :laskentakohde [nil "ei kohdetta tässä.."]
                                 :uusi-laskentakohde "Uusikohde"
                                 :yllapitoluokka 1
                                 :hinta 665.5}]}
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*)
                                                       FROM yllapito_muu_toteuma
                                                     WHERE urakka = " urakka-id
                                            " AND sopimus = " sopimus-id
                                            " AND pvm >= '2016-1-01' AND pvm <= '2016-12-31'")))
        laskentakohdelkm-ennen (ffirst (q
                                         (str "SELECT count(*)
                                                       FROM urakka_laskentakohde
                                                     WHERE urakka = " urakka-id)))
        res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-yllapito-toteumat +kayttaja-jvh+
                            hyotykuorma)
        maara-lisayksen-jalkeen (ffirst (q
                                          (str "SELECT count(*)
                                                       FROM yllapito_muu_toteuma
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
                                {:urakka-id urakka-id
                                 :vuosi 2016})]
    (is (= (count vastaus) 3))))

(deftest usean-tiemerkinnan-yks-hint-toteuman-tallennus-toimii
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        yllapitokohde-id (hae-tiemerkintaurakkaan-osoitettu-yllapitokohde urakka-id)
        toteumien-maara 10
        gen-tyo (fn [] (gen/generate (s/gen ::tt/tiemerkinnan-yksikkohintainen-tyo)))
        pyynto {:urakka-id urakka-id
                :vuosi 2017
                :toteumat (->> (mapv (fn [_]
                                       (loop [tyo (gen-tyo)]
                                         (if (contains? tyo :tr-numero)
                                           tyo
                                           (recur (gen-tyo)))))
                                     (range 1 (inc toteumien-maara)))
                               ;; Tee kohteista uusia
                               (mapv #(assoc % :id nil
                                             :yllapitokohde-id (get [yllapitokohde-id nil] (int (rand 2)))))
                               (mapv #(if (nil? (:yllapitokohde-id %))
                                        %
                                        (assoc % :hinta-kohteelle (or (:hinta-kohteelle %)
                                                                      "1234"))))
                               ;; Liitä osa toteumista ylläpitokohteeseen
                               (mapv #(if (int (rand 2))
                                        (assoc % :yllapitokohde-id yllapitokohde-id
                                               :hinta-kohteelle (or (:hinta-kohteelle %)
                                                                    "1234"))
                                        (dissoc % :yllapitokohde-id :hinta-kohteelle))))}
        maara-ennen-lisaysta (ffirst (q "SELECT COUNT(*) FROM tiemerkinnan_yksikkohintainen_toteuma;"))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-tiemerkinnan-yksikkohintaiset-tyot +kayttaja-jvh+
                          pyynto)
        maara-lisayksen-jalkeen (ffirst (q "SELECT COUNT(*) FROM tiemerkinnan_yksikkohintainen_toteuma;"))]
    (is (= (+ maara-ennen-lisaysta toteumien-maara) maara-lisayksen-jalkeen))))

(deftest tiemerkinnan-yks-hint-toteuma-kirjataan-oikein
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        testien-maara 50
        maara-ennen-testia (ffirst (q "SELECT COUNT(*) FROM tiemerkinnan_yksikkohintainen_toteuma;"))
        yllapitokohde-id (hae-tiemerkintaurakkaan-osoitettu-yllapitokohde urakka-id)]
    
    (loop [index 0]
      (let [selite (str "yksikkötesti" index)
            linkitettava-yllapitokohde-id (get [yllapitokohde-id nil] (int (rand 2)))
            gen-tyo (fn [] (gen/generate (s/gen ::tt/tiemerkinnan-yksikkohintainen-tyo)))
            tiemerkinnan-yksikkohintainen-tyo (if linkitettava-yllapitokohde-id
                                                (gen-tyo)
                                                (loop [tyo (gen-tyo)]
                                                  (if (contains? tyo :tr-numero)
                                                    tyo
                                                    (recur (gen-tyo)))))
            kirjattava-toteuma (as-> tiemerkinnan-yksikkohintainen-tyo toteuma
                                     ;; Tee tästä uusi toteuma
                                     (assoc toteuma
                                       :id nil
                                       :selite selite
                                       :paivamaara (gen/generate (s/gen ::tt/paivamaara))
                                       :poistettu false)
                                     ;; Liitä ylläpitokohde jos satuttiin arpomaan se
                                     (if linkitettava-yllapitokohde-id
                                       (assoc toteuma :yllapitokohde-id linkitettava-yllapitokohde-id
                                              :hinta-kohteelle (or (:hinta-kohteelle toteuma)
                                                                   "1234"))
                                       (dissoc toteuma :yllapitokohde-id)))
            pyynto {:urakka-id urakka-id
                    :vuosi (pvm/vuosi (:paivamaara kirjattava-toteuma))
                    :toteumat [kirjattava-toteuma]}
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-tiemerkinnan-yksikkohintaiset-tyot +kayttaja-jvh+
                                    pyynto)
            kirjatut-toteumat (filter #(= (:selite %) selite) vastaus)
            kirjattu-toteuma (first kirjatut-toteumat)]

        ;; Arvot on kirjattu oikein
        (is (= (count kirjatut-toteumat) 1))
        (is (= (:selite kirjattu-toteuma) selite))
        (is (= (:hintatyyppi kirjattu-toteuma) (:hintatyyppi kirjattava-toteuma)))
        (is (= (:yllapitoluokka kirjattu-toteuma) (if linkitettava-yllapitokohde-id
                                                    nil
                                                    (:yllapitoluokka kirjattava-toteuma))))
        (is (= (:pituus kirjattu-toteuma) (if linkitettava-yllapitokohde-id
                                            nil
                                            (:pituus kirjattava-toteuma))))
        (is (= (:yllapitokohde-id kirjattu-toteuma) (:yllapitokohde-id kirjattava-toteuma)))
        (is (= (:tr-numero kirjattu-toteuma) (if linkitettava-yllapitokohde-id
                                               nil
                                               (:tr-numero kirjattava-toteuma))))
        (is (= (clj-str/replace (format "%.2f" (bigdec (:hinta kirjattu-toteuma))) "-0," "0,")
               (clj-str/replace (format "%.2f" (bigdec (:hinta kirjattava-toteuma))) "-0," "0,")))
        (if linkitettava-yllapitokohde-id
          (is (= (:hinta-kohteelle kirjattava-toteuma) (:hinta-kohteelle kirjattu-toteuma)))
          (is (nil? (:hinta-kohteelle kirjattu-toteuma))))

        (when (< index (dec testien-maara))
          (recur (inc index)))))

    (let [maara-testin-jalkeen (ffirst (q "SELECT COUNT(*) FROM tiemerkinnan_yksikkohintainen_toteuma;"))]
      (is (= (+ maara-ennen-testia testien-maara) maara-testin-jalkeen)))))
