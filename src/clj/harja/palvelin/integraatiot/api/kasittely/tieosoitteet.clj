(ns harja.palvelin.integraatiot.api.kasittely.tieosoitteet
  (:require [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [harja.pvm :as pvm]))

(defn muunna-yllapitokohteen-tieosoitteet [vkm db kohteen-tienumero {:keys [id sijainti alikohteet] :as kohde}]
  (if-let [karttapvm (:karttapvm sijainti)]
    (let [karttapvm (parametrit/pvm-aika karttapvm)
          harjan-verkon-vpm (or (q-geometriapaivitykset/hae-karttapvm db) (pvm/nyt))
          muunnettavat-sijainnit (conj
                                   (mapv (fn [{{:keys [tunniste sijainti]} :alikohde}]
                                           (assoc sijainti :id (:id tunniste) :tie kohteen-tienumero))
                                         alikohteet)
                                   (assoc sijainti :id id :tie kohteen-tienumero))
          muunnetut-sijainnit (vkm/muunna-tieosoitteet-verkolta-toiselle
                                vkm
                                muunnettavat-sijainnit
                                harjan-verkon-vpm
                                karttapvm)
          muunnettu-kohteen-sijainti (if (some #(= id (:id %)) muunnetut-sijainnit)
                                       (merge sijainti (first (filter #(= id (:id %)) muunnetut-sijainnit)))
                                       sijainti)
          muunnetut-alikohteet (mapv (fn [{{:keys [tunniste sijainti] :as alikohde} :alikohde}]
                                       (if (some #(= (:id tunniste) (:id %)) muunnetut-sijainnit)
                                         (assoc
                                           alikohde
                                           :sijainti
                                           (merge sijainti (first (filter #(= (:id tunniste) (:id %)) muunnetut-sijainnit))))
                                         alikohde))
                                     alikohteet)]
      (assoc kohde :sijainti muunnettu-kohteen-sijainti :alikohteet muunnetut-alikohteet))
    kohde))