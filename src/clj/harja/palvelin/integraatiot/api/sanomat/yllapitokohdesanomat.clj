(ns harja.palvelin.integraatiot.api.sanomat.yllapitokohdesanomat
  (:require [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.tiemerkinta :as tiemerkinta]
            [clj-time.coerce :as c]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]))

(defn rakenna-sijainti [kohde karttapvm]
  {:numero (:tr-numero kohde)
   :aosa (:tr-alkuosa kohde)
   :aet (:tr-alkuetaisyys kohde)
   :losa (:tr-loppuosa kohde)
   :let (:tr-loppuetaisyys kohde)
   :ajr (:tr-ajorata kohde)
   :kaista (:tr-kaista kohde)
   :karttapvm karttapvm})

(defn rakenna-alikohde [alikohde karttapvm]
  {:alikohde {:tunniste {:id (:id alikohde)}
              :tunnus (:tunnus alikohde)
              :nimi (:nimi alikohde)
              :sijainti (rakenna-sijainti alikohde karttapvm)
              :toimenpide (:toimenpide alikohde)}})

(defn rakenna-kohde [{:keys [paallystysilmoitus] :as kohde} karttapvm]
  (let [k {:tunniste {:id (:id kohde)}
           :sopimus {:id (:sopimus kohde)}
           :kohdenumero (:kohdenumero kohde)
           :nimi (:nimi kohde)
           :tunnus (:tunnus kohde)
           :tyotyyppi (:yllapitokohdetyotyyppi kohde)
           :sijainti (rakenna-sijainti kohde karttapvm)
           :yllapitoluokka (:yllapitoluokka kohde)
           :keskimaarainen-vuorokausiliikenne (:keskimaarainen-vuorokausiliikenne kohde)
           :nykyinen-paallyste (paallystys-ja-paikkaus/hae-apin-paallyste-koodilla (:nykyinen-paallyste kohde))
           :alikohteet (mapv (fn [alikohde] (rakenna-alikohde alikohde karttapvm)) (:alikohteet kohde))
           :aikataulu {:kohde-aloitettu (:kohde-alku kohde)
                       :paallystys-aloitettu (:paallystys-alku kohde)
                       :paallystys-valmis (:paallystys-loppu kohde)
                       :valmis-tiemerkintaan (:valmis-tiemerkintaan kohde)
                       :tiemerkinta-takaraja (:tiemerkinta-takaraja kohde)
                       :tiemerkinta-aloitettu (:tiemerkinta-alku kohde)
                       :tiemerkinta-valmis (:tiemerkinta-loppu kohde)
                       :kohde-valmis (:kohde-valmis kohde)
                       :paallystysilmoitus {:takuupvm (:takuupvm paallystysilmoitus)}}}]
    (if (:yllapitokohdetyyppi kohde)
      (assoc k :tyyppi (:yllapitokohdetyyppi kohde))
      k)))

(defn rakenna-kohteet [yllapitokohteet karttapvm]
  {:yllapitokohteet
   (mapv (fn [kohde] (hash-map :yllapitokohde (rakenna-kohde kohde karttapvm)))
         yllapitokohteet)})