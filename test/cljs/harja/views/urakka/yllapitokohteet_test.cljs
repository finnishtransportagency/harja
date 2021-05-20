(ns harja.views.urakka.yllapitokohteet-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.pvm :refer [->pvm]]

    [harja.loki :refer [log]]
    [harja.domain.tierekisteri :as tr]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.tyokalut.functor :refer [fmap]]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop])
  (:require-macros [clojure.test.check.clojure-test :refer [defspec]]
                   [clojure.test.check.properties :as prop]))

(def kohdeosat
  {1 {:nimi "Laivaniemi 1"
      :tr-numero 1
      :tr-alkuosa 1
      :tr-alkuetaisyys 100
      :tr-loppuosa 2
      :tr-loppuetaisyys 200}
   2 {:nimi "Laivaniemi 2"
      :tr-numero 1
      :tr-alkuosa 2
      :tr-alkuetaisyys 200
      :tr-loppuosa 3
      :tr-loppuetaisyys 15}
   3 {:nimi "Laivaniemi 3"
      :tr-numero 1
      :tr-alkuosa 3
      :tr-alkuetaisyys 15
      :tr-loppuosa 3
      :tr-loppuetaisyys 4242}})

(def osien-pituus {1 6666
                   2 7777
                   3 5353})


(defn pituus [osa]
  (tr/laske-tien-pituus osien-pituus osa))

(defn pituus-yht [kohdeosat]
  (reduce +
          (map pituus
               (vals kohdeosat))))

(def alku (juxt :tr-alkuosa :tr-alkuetaisyys))
(def loppu (juxt :tr-loppuosa :tr-loppuetaisyys))

(defn avaimet [kohdeosat]
  (into #{} (keys kohdeosat)))

(deftest uuden-kohteen-lisaaminen
  (let [vanhat-kohdeosat kohdeosat
        uudet-kohdeosat (yllapitokohteet/pilko-paallystekohdeosa kohdeosat 1 {})]
    (is (= #{1 2 3 4} (avaimet uudet-kohdeosat)))

    (is (= (loppu (get vanhat-kohdeosat 1))
           (loppu (get uudet-kohdeosat 2)))
        "Rivin lisääminen siirtää loppuosa seuraavalle riville")

    (is (= [nil nil]
           (loppu (get uudet-kohdeosat 1))
           (alku (get uudet-kohdeosat 2)))
        "Rivin loppu ja seuraavan alku ovat tyhjiä lisäämisen jälkeen")))

(deftest valissa-olevan-osan-poistaminen
  (let [vanhat-kohdeosat kohdeosat
        uudet-kohdeosat (yllapitokohteet/poista-kohdeosa kohdeosat 2)]
    (is (= #{1 2} (avaimet uudet-kohdeosat)))
    (is (= (alku (get vanhat-kohdeosat 1))
           (alku (get uudet-kohdeosat 1)))
        "Ensimmäisen osan alku ei muutu")

    (is (= (loppu (get uudet-kohdeosat 2))
           (loppu (get vanhat-kohdeosat 3)))
        "Loppu siirtyy yhdellä aiemmaksi")))

(defn tierekisteriosoite [tie osien-pituus osa-min osa-max]
  (gen/fmap
   #(merge {:tr-numero tie}
           (zipmap [:tr-alkuosa :tr-alkuetaisyys
                    :tr-loppuosa :tr-loppuetaisyys]
                   %))
   (gen/bind
    (gen/such-that #(<= (first %) (second %))
                   (gen/vector (gen/choose osa-min osa-max) 2)
                   100)
    #(let [a-pit (get osien-pituus (first %))
           l-pit (get osien-pituus (second %))]
       (gen/such-that
        (fn [[aosa alkuet losa loppuet]]
          (or (< aosa losa)
              (> (- loppuet alkuet) 2)))
        (gen/tuple (gen/return (first %))
                   (gen/choose 1 (dec a-pit))
                   (gen/return (second %))
                   (gen/choose 1 (dec l-pit)))
        100)))))


(defn tien-kohta
  "Paluttaa generaattorin, joka valitsee sattumanvaraisen kohdan annetusta tiestä.
  Generaattorin arvo on tuple, jossa on annettu tie ja tuple [katkaisuosa katkaisuetäisyys]."
  [osien-pituus
   {aosa :tr-alkuosa alkuet :tr-alkuetaisyys
    losa :tr-loppuosa loppuet :tr-loppuetaisyys :as tie}]
  (gen/tuple (gen/return tie)
             (if (= aosa losa)
               ;; Jos alku- ja loppuosa on sama, ota kohta etäisyyksien väliltä
               (gen/tuple (gen/return aosa)
                          (gen/choose (inc alkuet) (dec loppuet)))

               ;; Muuten arvotaan osa, josta arvotaan
               (gen/bind (gen/choose aosa losa)
                         (fn [osa]
                           (gen/tuple
                            (gen/return osa)
                            (cond
                              (= osa aosa)
                              (if (> (inc alkuet) (dec (get osien-pituus osa)))
                                (gen/return alkuet)
                                (gen/choose (inc alkuet)
                                            (dec (get osien-pituus osa))))

                              (= osa losa)
                              (if (= 1 loppuet)
                                (gen/return 1)
                                (gen/choose 1 (dec loppuet)))

                              :default
                              (gen/choose 1 (get osien-pituus osa)))))))))

(deftest paallystyskohteiden-sorttaus
  (let [kohdenumeroita ["1" "308a" "11" "2" "L12" "300" nil "L11" "308b"]
        oikea-jarjestys [nil "1" "2" "11" "L11" "L12" "300" "308a" "308b"]]
    (is (= oikea-jarjestys (sort-by yllapitokohteet-domain/kohdenumero-str->kohdenumero-vec kohdenumeroita)))))
