(ns harja.views.urakka.yllapitokohteet-test
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]))

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

(def alku (juxt :tr-alkuosa :tr-alkuetaisyys))
(def loppu (juxt :tr-loppuosa :tr-loppuetaisyys))

(defn avaimet [kohdeosat]
  (into #{} (keys kohdeosat)))

(deftest uuden-kohteen-lisaaminen
  (let [uudet-kohdeosat (yllapitokohteet/pilko-paallystekohdeosa kohdeosat 1 {})]
    (is (= #{1 2 3 4} (avaimet uudet-kohdeosat)))
    (is (= (loppu (get kohdeosat 1))
           (loppu (get uudet-kohdeosat 2)))
      "Rivin lisääminen siirtää loppuosa seuraavalle riville")
    (is (= [nil nil]
           (loppu (get uudet-kohdeosat 1))
           (alku (get uudet-kohdeosat 2)))
      "Rivin loppu ja seuraavan alku ovat tyhjiä lisäämisen jälkeen")))

(deftest valissa-olevan-osan-poistaminen
  (let [uudet-kohdeosat (yllapitokohteet/poista-kohdeosa kohdeosat 2)]
    (is (= #{1 2} (avaimet uudet-kohdeosat)))
    (is (= (alku (get kohdeosat 1))
           (alku (get uudet-kohdeosat 1)))
      "Ensimmäisen osan alku ei muutu")
    (is (= (loppu (get uudet-kohdeosat 2))
           (loppu (get kohdeosat 3)))
      "Loppu siirtyy yhdellä aiemmaksi")))

(deftest paallystyskohteiden-sorttaus
  (let [kohdenumeroita ["1" "308a" "11" "2" "L12" "300" nil "L11" "308b"]
        oikea-jarjestys [nil "1" "2" "11" "L11" "L12" "300" "308a" "308b"]]
    (is (= oikea-jarjestys
           (sort-by yllapitokohteet-domain/kohdenumero-str->kohdenumero-vec
             kohdenumeroita)))))
