(ns harja.palvelin.integraatiot.api.sanomat.yllapitokohdesanomat
  (:require [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]))

(defn rakenna-sijainti [kohde]
  {:numero (:tr-numero kohde)
   :aosa (:tr-alkuosa kohde)
   :aet (:tr-alkuetaisyys kohde)
   :losa (:tr-loppuosa kohde)
   :let (:tr-loppuetaisyys kohde)
   :ajr (:tr-ajorata kohde)
   :kaista (:tr-kaista kohde)})

(defn rakenna-alikohde [alikohde]
  {:alikohde {:tunniste {:id (:id alikohde)}
              :tunnus (:tunnus alikohde)
              :nimi (:nimi alikohde)
              :sijainti (rakenna-sijainti alikohde)
              :toimenpide (:toimenpide alikohde)}})

(defn rakenna-kohde [kohde]
  {:tunniste {:id (:id kohde)}
   :sopimus {:id (:sopimus kohde)}
   :kohdenumero (:kohdenumero kohde)
   :nimi (:nimi kohde)
   :tyyppi (:yllapitokohdetyyppi kohde)
   :tyotyyppi (:yllapitokohdetyotyyppi kohde)
   :sijainti (rakenna-sijainti kohde)
   :yllapitoluokka (:yllapitoluokka kohde)
   :keskimaarainen-vuorokausiliikenne (:keskimaarainen-vuorokausiliikenne kohde)
   :nykyinen-paallyste (paallystys-ja-paikkaus/hae-apin-paallyste-koodilla (:nykyinen-paallyste kohde))
   :alikohteet (mapv (fn [alikohde] (rakenna-alikohde alikohde)) (:alikohteet kohde))})

(defn rakenna-kohteet [yllapitokohteet]
  {:yllapitokohteet
   (mapv (fn [kohde] (hash-map :yllapitokohde (rakenna-kohde kohde)))
         yllapitokohteet)})