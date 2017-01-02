(ns harja.tiedot.urakka.paallystys
  "Päällystyksen tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka :as urakka]
    [harja.domain.tierekisteri :as tr-domain])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(def paallystyskohteet-nakymassa? (atom false))
(def paallystysilmoitukset-nakymassa? (atom false))

(defn hae-paallystysilmoitukset [urakka-id sopimus-id vuosi]
  (k/post! :urakan-paallystysilmoitukset {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :vuosi vuosi}))

(defn hae-paallystysilmoitus-paallystyskohteella [urakka-id paallystyskohde-id]
  (k/post! :urakan-paallystysilmoitus-paallystyskohteella {:urakka-id urakka-id
                                                           :paallystyskohde-id paallystyskohde-id}))

(defn tallenna-paallystysilmoitus! [urakka-id sopimus-id lomakedata]
  (k/post! :tallenna-paallystysilmoitus {:urakka-id urakka-id
                                         :sopimus-id sopimus-id
                                         :paallystysilmoitus lomakedata}))

(def paallystysilmoitukset
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @urakka/valittu-sopimusnumero
               nakymassa? @paallystysilmoitukset-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (hae-paallystysilmoitukset valittu-urakka-id valittu-sopimus-id vuosi))))

(defonce paallystysilmoitus-lomakedata (atom nil)) ; Vastaa rakenteeltaan päällystysilmoitus-taulun sisältöä

(def paallystysilmoituslomake-lukittu? (reaction (let [_ @lukko/nykyinen-lukko]
                                                   (lukko/nykyinen-nakyma-lukittu?))))

(defonce karttataso-paallystyskohteet (atom false))

(def yllapitokohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @urakka/valittu-sopimusnumero
               nakymassa? @paallystyskohteet-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (yllapitokohteet/hae-yllapitokohteet valittu-urakka-id valittu-sopimus-id vuosi))))

(def yhan-paallystyskohteet
  (reaction-writable
    (let [kohteet @yllapitokohteet
          yhan-paallystyskohteet (when kohteet
                                   (filter
                                     #(and (yllapitokohteet/yha-kohde? %)
                                           (= (:yllapitokohdetyotyyppi %) :paallystys))
                                     kohteet))]
      (tr-domain/jarjesta-kohteiden-kohdeosat yhan-paallystyskohteet))))

(def harjan-paikkauskohteet
  (reaction-writable
    (let [kohteet @yllapitokohteet
          harjan-paikkauskohteet (when kohteet
                                   (filter
                                     #(and (not (yllapitokohteet/yha-kohde? %))
                                           (= (:yllapitokohdetyotyyppi %) :paikkaus))
                                     kohteet))]
      (tr-domain/jarjesta-kohteiden-kohdeosat harjan-paikkauskohteet))))

(def kohteet-yhteensa
  (reaction (concat @yhan-paallystyskohteet @harjan-paikkauskohteet)))

(defonce paallystyskohteet-kartalla
         (reaction (let [taso @karttataso-paallystyskohteet
                         kohderivit @yhan-paallystyskohteet
                         ilmoitukset @paallystysilmoitukset
                         avoin-paallystysilmoitus (:paallystyskohde-id @paallystysilmoitus-lomakedata)]
                     (when (and taso
                                (or kohderivit ilmoitukset))
                       (kartalla-esitettavaan-muotoon
                         (concat (map #(assoc % :paallystyskohde-id (:id %)) ;; yhtenäistä id kohde ja toteumariveille
                                      kohderivit)
                                 ilmoitukset)
                         @paallystysilmoitus-lomakedata
                         [:paallystyskohde-id]
                         (comp
                           (mapcat (fn [kohde]
                                     (keep (fn [kohdeosa]
                                             (assoc (merge kohdeosa
                                                           (dissoc kohde :kohdeosat))
                                               :tila (yllapitokohteet/yllapitokohteen-tila kohde)
                                               :avoin? (= (:paallystyskohde-id kohde) avoin-paallystysilmoitus)
                                               :kohdeosa kohdeosa
                                               :nimi (:nimi kohde)))
                                           (:kohdeosat kohde))))
                           (keep #(and (:sijainti %) %))
                           (map #(assoc % :tyyppi-kartalla :paallystys))))))))

(defonce kohteet-yha-lahetyksessa (atom nil))
