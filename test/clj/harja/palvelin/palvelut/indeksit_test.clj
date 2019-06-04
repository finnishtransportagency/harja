(ns harja.palvelin.palvelut.indeksit-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.indeksit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [harja.domain.urakka :as urakka]))


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




(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

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
        {:keys [hoito tiemerkinta paallystys vesivayla-kanavien-hoito]}
        (group-by :urakkatyyppi indeksit)]
    (is (some #(= "MAKU 2005" (:indeksinimi %)) hoito))
    (is (some #(= "MAKU 2010" (:indeksinimi %)) hoito))
    (is (some #(= "MAKU 2015" (:indeksinimi %)) hoito))
    (is (not (some #(= "MAKU 2008" (:indeksinimi %)) hoito))) ;tällaista ei käytetä
    (is (some #(= "MAKU 2010" (:indeksinimi %)) tiemerkinta))
    (is (some #(= "Platts: FO 3,5%S CIF NWE Cargo" (:indeksinimi %)) paallystys))
    (is (some #(= "bitumi" (:raakaaine %)) paallystys))
    (is (some #(= "ABWGL03" (:koodi %)) paallystys))
    (is (some #(str/includes? (:indeksinimi %) "Platts") paallystys))
    (is (some #(= "Palvelujen tuottajahintaindeksi 2010" (:indeksinimi %)) vesivayla-kanavien-hoito))
    (is (some #(= "Palvelujen tuottajahintaindeksi 2015" (:indeksinimi %)) vesivayla-kanavien-hoito))))

(deftest paallystysurakan-indeksitietojen-haku
  (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :paallystysurakan-indeksitiedot
                                 +kayttaja-jvh+
                                 {::urakka/id 5})]
    (is (= 2 (count indeksit)))
    ;; spec'atun palvelun vastauksen muodollista pätevyyttä ei tarvi tarkistella
    (is (= "Platts: testiindeksi XYZ" (:indeksinimi (:indeksi (first indeksit)))))
    (is (=marginaalissa? 225.0 (:arvo (:indeksi (first indeksit)))))))

(deftest paallystysurakan-indeksitiedot-tallennus
  (let [hyotykuorma [{:id -1 :urakka 5
                      :lahtotason-vuosi 2014 :lahtotason-kuukausi 9
                      :indeksi {:id 8 :urakkatyyppi :paallystys
                                :indeksinimi "Platts: Propane CIF NWE 7kt+"
                                :raakaaine "nestekaasu"
                                :koodi "PMUEE03"}}]
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-paallystysurakan-indeksitiedot
                                +kayttaja-jvh+
                                hyotykuorma)]
    ;; Lisättiin yksi, joten nyt indeksejä on kolme
    (is (= 3 (count vastaus)) "indeksivuosien lukumäärä tallennuksen jälkeen")

    (testing "Indeksin merkitseminen poistetuksi"
      (let [hyotykuorma (assoc-in vastaus [0 :poistettu] true)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-paallystysurakan-indeksitiedot
                                    +kayttaja-jvh+
                                    hyotykuorma)]
        (is (= 2 (count vastaus)) "indeksejä on 2 poiston jälkeen")))))

(deftest laske-vesivaylaurakan-indeksilaskennan-perusluku
  (let [ur (hae-helsingin-vesivaylaurakan-id)
        perusluku (ffirst (q (str "select * from indeksilaskennan_perusluku(" ur ");")))]
    ; (103.9+105.2+106.2) / 3 = 105.1M tammi, helmi- ja maaliskuun keskiarvo urakan alkuvuonna
    (is (= 105.1M perusluku))))

(deftest laske-tampereen-2017-alkavan-hoitourakan-indeksilaskennan-perusluku
  (let [ur (hae-tampereen-alueurakan-2017-2022-id)
        perusluku (ffirst (q (str "select * from indeksilaskennan_perusluku(" ur ");")))]
    ; alkupvm:ää edeltävän vuoden syys-, loka- ja marraskuun keskiarvo urakan alkuvuonna
    (is (= 115.4M perusluku))))