(ns harja.palvelin.integraatiot.api.kasittely.tieosoitteet
  (:require [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [harja.kyselyt.geometriapaivitykset :as q-geometriapaivitykset]
            [harja.pvm :as pvm]))

(defn- sisaltaa-sijainnin? [osoitteet vkm-id]
  (some #(= vkm-id (:vkm-id %)) osoitteet))

(defn- hae-sijainti [osoitteet vkm-id]
  (first (filter #(= vkm-id (:vkm-id %)) osoitteet)))

(defn muunna-yllapitokohteen-tieosoitteet [vkm db kohteen-tienumero {:keys [sijainti alikohteet] :as kohde}]
  (if-let [karttapvm (:karttapvm sijainti)]
    (let [paakohteen-vkm-id "paakohde"
          karttapvm (parametrit/pvm-aika karttapvm)
          harjan-verkon-vpm (or (q-geometriapaivitykset/hae-karttapvm db) (pvm/nyt))
          muunnettavat-alikohteet (map-indexed (fn [i {sijainti :sijainti :as alikohde}]
                                                 (assoc alikohde
                                                   :vkm-id (str "alikohde-" i)
                                                   :sijainti (assoc sijainti :tie kohteen-tienumero)))
                                               alikohteet)
          muunnettavat-sijainnit (conj
                                   (map #(assoc (:sijainti %) :vkm-id (:vkm-id %)) muunnettavat-alikohteet)
                                   (assoc sijainti :vkm-id paakohteen-vkm-id :tie kohteen-tienumero))
          muunnetut-sijainnit (vkm/muunna-tieosoitteet-verkolta-toiselle
                                vkm
                                muunnettavat-sijainnit
                                harjan-verkon-vpm
                                karttapvm)
          muunnettu-kohteen-sijainti (if (sisaltaa-sijainnin? muunnetut-sijainnit paakohteen-vkm-id)
                                       (merge sijainti (hae-sijainti muunnetut-sijainnit paakohteen-vkm-id))
                                       sijainti)
          muunnetut-alikohteet (mapv (fn [{:keys [sijainti vkm-id] :as alikohde}]
                                       (if (sisaltaa-sijainnin? muunnetut-sijainnit vkm-id)
                                         (-> alikohde
                                             (assoc :sijainti (dissoc
                                                                (merge sijainti (hae-sijainti muunnetut-sijainnit vkm-id))
                                                                :vkm-id))
                                             (dissoc :vkm-id))
                                         (dissoc alikohde :vkm-id)))
                                     muunnettavat-alikohteet)]
      (do
        (-> kohde
           (assoc :sijainti muunnettu-kohteen-sijainti :alikohteet muunnetut-alikohteet)
           (dissoc :vkm-id))))
    kohde))