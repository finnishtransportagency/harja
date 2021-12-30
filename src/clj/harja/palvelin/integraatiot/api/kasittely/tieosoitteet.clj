(ns harja.palvelin.integraatiot.api.kasittely.tieosoitteet
  (:require [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [harja.kyselyt.geometriapaivitykset :as q-geometriapaivitykset]
            [harja.pvm :as pvm]))

(defn- sisaltaa-sijainnin? [osoitteet vkm-id]
  (some #(= vkm-id (:vkm-id %)) osoitteet))

(defn- hae-sijainti [osoitteet vkm-id]
  (first (filter #(= vkm-id (:vkm-id %)) osoitteet)))

(defn yhdista-osoitteet [alkuperaiset muunnetut-sijainnit]
  (mapv (fn [{:keys [sijainti vkm-id] :as a}]
          (if (sisaltaa-sijainnin? muunnetut-sijainnit vkm-id)
            (-> a
                (assoc :sijainti (dissoc
                                   (merge sijainti (hae-sijainti muunnetut-sijainnit vkm-id))
                                   :vkm-id))
                (dissoc :vkm-id))
            (dissoc a :vkm-id)))
        alkuperaiset))

;; FIXME: Käyttää vanhoja VKM-parametreja. Päivitettävä, jos tätä käytetään vielä.
(defn muunna-yllapitokohteen-tieosoitteet [vkm db kohteen-tienumero karttapvm {:keys [sijainti alikohteet] :as kohde}]
  (if karttapvm
    (let [paakohteen-vkm-id "paakohde"
          muunnettavat-alikohteet (map-indexed (fn [i {sijainti :sijainti :as alikohde}]
                                                 (assoc alikohde
                                                   :vkm-id (str "alikohde-" i)
                                                   :sijainti (assoc sijainti :tie (or
                                                                                    (:numero sijainti)
                                                                                    (:tie sijainti)
                                                                                    kohteen-tienumero))))
                                               alikohteet)
          muunnettavat-sijainnit (conj
                                   (map #(assoc (:sijainti %) :vkm-id (:vkm-id %)) muunnettavat-alikohteet)
                                   (assoc sijainti :vkm-id paakohteen-vkm-id :tie kohteen-tienumero))
          muunnetut-sijainnit (vkm/muunna-tieosoitteet-verkolta-toiselle
                                vkm
                                muunnettavat-sijainnit)
          muunnettu-kohteen-sijainti (if (sisaltaa-sijainnin? muunnetut-sijainnit paakohteen-vkm-id)
                                       (merge sijainti (hae-sijainti muunnetut-sijainnit paakohteen-vkm-id))
                                       sijainti)
          muunnetut-alikohteet (yhdista-osoitteet muunnettavat-alikohteet muunnetut-sijainnit)]
      (-> kohde
          (assoc :sijainti muunnettu-kohteen-sijainti :alikohteet muunnetut-alikohteet)
          (dissoc :vkm-id)))
    kohde))
