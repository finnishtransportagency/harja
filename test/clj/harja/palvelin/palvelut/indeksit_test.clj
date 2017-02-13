(ns harja.palvelin.palvelut.indeksit-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.indeksit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.string :as str]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start 
                     (component/system-map
                      :db (tietokanta/luo-tietokanta testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :indeksit (component/using
                                  (->Indeksit)
                                  [:http-palvelin :db])))))
  
  (testit)
  (alter-var-root #'jarjestelma component/stop))



  
(use-fixtures :once jarjestelma-fixture)

;; maku 2005 vuonna 2013
;; ["MAKU 2005" 2013] {:vuosi 2013, 12 110.1, 11 110.5, 1 109.2}}

(deftest kaikki-indeksit-haettu-oikein
  (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :indeksit +kayttaja-jvh+)
        maku-2005-2013 (get indeksit ["MAKU 2005" 2013])]
    (is (> (count indeksit) 0))
    (is (= (count maku-2005-2013) 13))
    (is (every? some? maku-2005-2013))
    (is (= (:vuosi maku-2005-2013) 2013))
    (is (= (get maku-2005-2013 12) (float 105.2))))) ;; <- odota ongelmia floatien kanssa



;; HAR-4035 bugin verifiointi
(deftest kuukauden-indeksikorotuksen-laskenta
  (let [korotus
        (ffirst (q (str "SELECT korotus from laske_kuukauden_indeksikorotus
 (2016, 10, 'MAKU 2005', 387800, 135.4);")))]
    (is (=marginaalissa? korotus 1145.64))))

(deftest urakkatyypin-indeksien-haku
  (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :urakkatyypin-indeksit +kayttaja-jvh+)
        hoidon (filter #(= :hoito (:urakkatyyppi %)) indeksit)
        tiemerkinnan (filter #(= :tiemerkinta (:urakkatyyppi %)) indeksit)
        paallystyksen (filter #(= :paallystys (:urakkatyyppi %)) indeksit)]
    (is (some #(= "MAKU 2005" (:indeksinimi %)) hoidon))
    (is (some #(= "MAKU 2010" (:indeksinimi %)) hoidon))
    (is (not (some #(= "MAKU 2008" (:indeksinimi %)) hoidon))) ;tällaista ei käytetä
    (is (some #(= "MAKU 2010" (:indeksinimi %)) tiemerkinnan))
    (is (some #(= "Platts: FO 3,5%S CIF NWE Cargo" (:indeksinimi %)) paallystyksen))
    (is (some #(= "raskas_polttooljy" (:raakaaine %)) paallystyksen))
    (is (some #(= "ABWGL03" (:koodi %)) paallystyksen))
    (is (some #(str/includes? (:indeksinimi %) "Platts") paallystyksen))))

(deftest paallystysurakan-indeksitietojen-haku
  (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :paallystysurakan-indeksitiedot
                                 +kayttaja-jvh+
                                 {:urakka-id 5})
        rivi-2016 (first (filter #(= 2016 (:urakkavuosi %)) indeksit))
        rivi-2017 (first (filter #(= 2017 (:urakkavuosi %)) indeksit))]
    (is (integer? (:id rivi-2016)) "id on")
    (is (integer? (:id rivi-2017)) "id on")
    (is (not= (:id rivi-2016) (:id rivi-2017)) "eri vuonna eri id")
    (is (= (:raskas rivi-2016) {:id 10
                                :indeksinimi "Platts: testiindeksi XYZ"
                                :koodi "TESTIKOODI"
                                :lahtotason-arvo 225.00M
                                :raakaaine "raskas_polttooljy"}))
    (is (= (:nestekaasu rivi-2016) {:raakaaine "kevyt_polttooljy", :id 6, :koodi "ABWHK03"
                                    :indeksinimi "Platts: ULSD 10ppmS CIF NWE Cargo" :lahtotason-arvo 123.45M}))
    (is (= (:kevyt rivi-2016) {:indeksinimi "Platts: Propane CIF NWE 7kt+", :koodi "PMUEE03"
                               :raakaaine "nestekaasu", :id 8 :lahtotason-arvo 285.55M}))
    (is (= (:raskas rivi-2017) {:id 4
                                :indeksinimi "Platts: FO 3,5%S CIF NWE Cargo"
                                :koodi "ABWGL03"
                                :lahtotason-arvo 206.29M
                                :raakaaine "raskas_polttooljy"}))
    (is (= (:nestekaasu rivi-2017) {:id 6
                                    :indeksinimi "Platts: ULSD 10ppmS CIF NWE Cargo"
                                    :koodi "ABWHK03"
                                    :lahtotason-arvo 234.56M
                                    :raakaaine "kevyt_polttooljy"}))
    (is (= (:kevyt rivi-2017) {:id 8
                               :indeksinimi "Platts: Propane CIF NWE 7kt+"
                               :koodi "PMUEE03"
                               :lahtotason-arvo 271.02M
                               :raakaaine "nestekaasu"}))))

(deftest paallystysurakan-indeksitiedot-tallennus
  (let [hyotykuorma [{:id -1, :urakkavuosi 2015,
                      :lahtotason-vuosi 2014, :lahtotason-kuukausi 9
                      :raskas {:id 10, :urakkatyyppi :paallystys, :indeksinimi "Platts: testiindeksi XYZ", :raakaaine "raskas_polttooljy", :koodi "TESTIKOODI"},
                      :kevyt {:id 6, :urakkatyyppi :paallystys, :indeksinimi "Platts: ULSD 10ppmS CIF NWE Cargo", :raakaaine "kevyt_polttooljy", :koodi "ABWHK03"},
                      :nestekaasu {:id 8, :urakkatyyppi :paallystys, :indeksinimi "Platts: Propane CIF NWE 7kt+", :raakaaine "nestekaasu", :koodi "PMUEE03"}}]
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-paallystysurakan-indeksitiedot
                                +kayttaja-jvh+
                                {:urakka-id 5 :indeksitiedot hyotykuorma})
        rivi-2015 (first (filter #(= 2015 (:urakkavuosi %)) vastaus))
        rivi-2015-raskas (:raskas rivi-2015)
        rivi-2015-kevyt (:kevyt rivi-2015)
        rivi-2015-nestekaasu (:nestekaasu rivi-2015)
        pos-int? #(and (pos? %) (integer? %))]
    ;; Yleiset assertit
    (is (= 3 (count vastaus)) "indeksivuosien lukumäärä tallennuksen jälkeen")
    (is (= 2014 (:lahtotason-vuosi rivi-2015)) "lähtötason vuosi")
    (is (= 9 (:lahtotason-kuukausi rivi-2015)) "lähtötason kuukausi")
    (is (pos-int? (:id rivi-2015)) "positiivinen id annettiin")

    ;; Raaka-ainekohtaiset assertit
    (is (= "raskas_polttooljy" (:raakaaine rivi-2015-raskas)) "raaka-aine")
    (is (= "kevyt_polttooljy" (:raakaaine rivi-2015-kevyt)) "raaka-aine")
    (is (= "nestekaasu" (:raakaaine rivi-2015-nestekaasu)) "raaka-aine")
    (is (= nil (:lahtotason-arvo rivi-2015-raskas)) "lähtötason kuukausi")
    (is (= nil (:lahtotason-arvo rivi-2015-kevyt)) "lähtötason kuukausi")
    (is (= nil (:lahtotason-arvo rivi-2015-nestekaasu)) "lähtötason kuukausi")
    (is (= "TESTIKOODI" (:koodi rivi-2015-raskas)) "koodi")
    (is (= "ABWHK03" (:koodi rivi-2015-kevyt)) "koodi")
    (is (= "PMUEE03" (:koodi rivi-2015-nestekaasu)) "koodi")
    (is (= "Platts: testiindeksi XYZ" (:indeksinimi rivi-2015-raskas)) "indeksinimi")
    (is (= "Platts: ULSD 10ppmS CIF NWE Cargo" (:indeksinimi rivi-2015-kevyt)) "indeksinimi")
    (is (= "Platts: Propane CIF NWE 7kt+" (:indeksinimi rivi-2015-nestekaasu)) "indeksinimi")
    (is (pos-int? (:id rivi-2015-raskas)) "id")
    (is (pos-int? (:id rivi-2015-kevyt)) "id ")
    (is (pos-int? (:id rivi-2015-nestekaasu)) "id")))