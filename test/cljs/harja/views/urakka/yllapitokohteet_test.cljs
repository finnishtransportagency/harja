(ns harja.views.urakka.yllapitokohteet-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.pvm :refer [->pvm]]

    [harja.loki :refer [log]]))

(def kohdeosat
  {1 {:nimi "Laivaniemi 1"
      :tr-numero 1
      :tr-alkuosa 2
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

(def alku (juxt :tr-alkuosa :tr-alkuetaisyys))
(def loppu (juxt :tr-loppuosa :tr-loppuetaisyys))

(defn avaimet [kohdeosat]
  (into #{} (keys kohdeosat)))

(deftest uuden-kohteen-lisaaminen
  (let [vanhat-kohdeosat kohdeosat
        uudet-kohdeosat (yllapitokohteet/lisaa-uusi-kohdeosa kohdeosat 1)]
    (is (= #{1 2 3 4} (avaimet uudet-kohdeosat)))

    (is (= (loppu (get vanhat-kohdeosat 1))
           (loppu (get uudet-kohdeosat 2)))
        "Rivin lisääminen siirtää loppuosa seuraavalle riville")

    (is (= [nil nil]
           (loppu (get uudet-kohdeosat 1))
           (alku (get uudet-kohdeosat 2)))
        "Rivin loppu ja seuraavan alku ovat tyhjiä lisäämisen jälkeen")))

(deftest ensimmaisen-osan-poistaminen
  (let [vanhat-kohdeosat kohdeosat
        uudet-kohdeosat (yllapitokohteet/poista-kohdeosa kohdeosat 1)]
    (is (= #{1 2} (avaimet uudet-kohdeosat)))

    (is (= (alku (get vanhat-kohdeosat 1))
           (alku (get uudet-kohdeosat 1)))
        "Alku pysyy samana vaikka ensimmäisen osan poistaa")

    (is (= (loppu (get vanhat-kohdeosat 2))
           (loppu (get uudet-kohdeosat 1)))
        "Seuraavan rivin loppu siirtyy ensimmäiselle riville")))

(deftest viimeisen-osan-poistaminen
  (let [vanhat-kohdeosat kohdeosat
        uudet-kohdeosat (yllapitokohteet/poista-kohdeosa kohdeosat 3)]

    (is (= #{1 2} (avaimet uudet-kohdeosat)))
    (is (= (loppu (get uudet-kohdeosat 2))
           (loppu (get vanhat-kohdeosat 3)))
        "Loppu siirtyy edellisen rivin lopuksi")))

(deftest valissa-olevan-osan-poistaminen
  (let [vanhat-kohdeosat kohdeosat
        uudet-kohdeosat (yllapitokohteet/poista-kohdeosa kohdeosat 2)]
    (is (= #{1 2} (avaimet uudet-kohdeosat)))
    (is (= (loppu (get uudet-kohdeosat 1))
           (alku (get vanhat-kohdeosat 3))))))
