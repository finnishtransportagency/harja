(ns harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset
  (:require [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]))

(defn rivin-toiminnot-sarake
  [rivi osa e! app voi-muokata? kohdeosat-atom]
  (let [kohdeosat-muokkaa! (fn [uudet-kohdeosat-fn]
                             (let [vanhat-kohdeosat @kohdeosat-atom
                                   uudet-kohdeosat (uudet-kohdeosat-fn vanhat-kohdeosat)]
                               (swap! kohdeosat-atom (fn [_]
                                                       uudet-kohdeosat))))
        lisaa-osa-fn (fn [index]
                       (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                             (yllapitokohteet/lisaa-uusi-kohdeosa vanhat-kohdeosat (inc index) {}))))
        poista-osa-fn (fn [index]
                        (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                              (yllapitokohteet/poista-kohdeosa vanhat-kohdeosat (inc index)))))]
    (fn [rivi {:keys [index]} voi-muokata?]
      (let [yllapitokohde (-> app :paallystysilmoitus-lomakedata
                              :perustiedot
                              (select-keys [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]))]
        [:span.tasaa-oikealle
         [napit/yleinen-ensisijainen ""
          lisaa-osa-fn
          {:ikoni (ikonit/livicon-plus)
           :disabled (or (not (:kirjoitusoikeus? app))
                         (not voi-muokata?))
           :luokka "napiton-nappi btn-xs"
           :toiminto-args [index]}]
         [napit/kielteinen ""
          poista-osa-fn
          {:ikoni (ikonit/livicon-trash)
           :disabled (or (not (:kirjoitusoikeus? app))
                         (not voi-muokata?))
           :luokka "napiton-nappi btn-xs"
           :toiminto-args [index]}]])))
  )


