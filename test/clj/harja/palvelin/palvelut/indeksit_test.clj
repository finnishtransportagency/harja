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
        rivi-2016 (first (filter #(= 2016 (:urakkavuosi %)) indeksit))]
    (is (= (:raskas rivi-2016) {:raakaaine "raskas_polttooljy", :id 4,
                           :indeksinimi "Platts: FO 3,5%S CIF NWE Cargo", :koodi "ABWGL03" :lahtotason-arvo 225.00M}))
    (is (= (:kevyt rivi-2016) {:indeksinimi "Platts: Propane CIF NWE 7kt+", :koodi "PMUEE03"
                          :raakaaine "nestekaasu", :id 8 :lahtotason-arvo 285.55M}))
    (is (= (:nestekaasu rivi-2016) {:raakaaine "kevyt_polttooljy", :id 6, :koodi "ABWHK03"
                               :indeksinimi "Platts: ULSD 10ppmS CIF NWE Cargo" :lahtotason-arvo 123.45M}))))