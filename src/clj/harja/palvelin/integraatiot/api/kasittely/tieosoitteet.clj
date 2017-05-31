(ns harja.palvelin.integraatiot.api.kasittely.tieosoitteet
  (:require [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [harja.kyselyt.geometriapaivitykset :as q-geometriapaivitykset]
            [harja.pvm :as pvm]))

(defn- sisaltaa-sijainnin? [osoitteet vkm-id]
  (some #(= vkm-id (:id %)) osoitteet))

(defn- hae-sijainti [osoitteet vkm-id]
  (first (filter #(= vkm-id (:id %)) osoitteet)))

(defn muunna-yllapitokohteen-tieosoitteet [vkm db kohteen-tienumero {:keys [sijainti alikohteet] :as kohde}]
  (if-let [karttapvm (:karttapvm sijainti)]
    (let [paakohteen-vkm-id "paakohde"
          karttapvm (parametrit/pvm-aika karttapvm)
          harjan-verkon-vpm (or (q-geometriapaivitykset/hae-karttapvm db) (pvm/nyt))
          muunnettavat-sijainnit (conj
                                   (map-indexed (fn [i {sijainti :sijainti}]
                                                  (assoc sijainti :vkm-id (str "alikohde" i) :tie kohteen-tienumero))
                                                alikohteet)
                                   (assoc sijainti :vkm-id paakohteen-vkm-id :tie kohteen-tienumero))
          muunnetut-sijainnit (vkm/muunna-tieosoitteet-verkolta-toiselle
                                vkm
                                muunnettavat-sijainnit
                                harjan-verkon-vpm
                                karttapvm)
          muunnettu-kohteen-sijainti (if (sisaltaa-sijainnin? muunnetut-sijainnit paakohteen-vkm-id)
                                       (merge sijainti (hae-sijainti muunnetut-sijainnit paakohteen-vkm-id))
                                       sijainti)
          muunnetut-alikohteet (mapv (fn [{:keys [vkm-id sijainti] :as alikohde}]
                                       (println "--->>> alikohde " alikohde)
                                       (if (sisaltaa-sijainnin? vkm-id muunnetut-sijainnit)
                                         (-> alikohde
                                             (assoc :sijainti (merge sijainti (hae-sijainti muunnetut-sijainnit vkm-id)))
                                             (dissoc :vkm-id))
                                         alikohde))
                                     alikohteet)]
      (-> kohde
          (assoc :sijainti muunnettu-kohteen-sijainti :alikohteet muunnetut-alikohteet)
          (dissoc :vkm-id)))
    kohde))