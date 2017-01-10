(ns harja.tiedot.urakka.paikkaus
  "Paikkauksen tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka :as urakka])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defonce kohdeluettelossa? (atom false))
(defonce paikkausilmoitukset-nakymassa? (atom false))

(defn hae-paikkausilmoitukset [urakka-id sopimus-id vuosi]
  (k/post! :urakan-paikkausilmoitukset {:urakka-id urakka-id
                                        :sopimus-id sopimus-id
                                        :vuosi vuosi}))

(defn hae-paikkausilmoitus-paikkauskohteella [urakka-id sopimus-id paikkauskohde-id]
  (k/post! :urakan-paikkausilmoitus-paikkauskohteella {:urakka-id urakka-id
                                                       :sopimus-id sopimus-id
                                                       :paikkauskohde-id paikkauskohde-id}))

(defn tallenna-paikkausilmoitus! [urakka-id sopimus-id lomakedata]
  (k/post! :tallenna-paikkausilmoitus {:urakka-id urakka-id
                                       :sopimus-id sopimus-id
                                       :paikkausilmoitus lomakedata}))

(def paikkausilmoituslomake-lukittu? (reaction (let [_ @lukko/nykyinen-lukko]
                                                 (lukko/nykyinen-nakyma-lukittu?))))

(defonce karttataso-paikkauskohteet (atom false))

(defonce paikkausilmoitukset
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @urakka/valittu-sopimusnumero
               nakymassa? @paikkausilmoitukset-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (hae-paikkausilmoitukset valittu-urakka-id valittu-sopimus-id vuosi))))

(defonce paikkausilmoitus-lomakedata (atom nil)) ; Vastaa rakenteeltaan paikkausilmoitus-taulun sisältöä

(def yllapitokohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @urakka/valittu-sopimusnumero
               nakymassa? @kohdeluettelossa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (yllapitokohteet/hae-yllapitokohteet valittu-urakka-id valittu-sopimus-id vuosi))))

(def paikkauskohteet (reaction-writable (let [kohteet @yllapitokohteet]
                                          (when kohteet
                                            (filterv
                                              #(= (:yllapitokohdetyotyyppi %) :paikkaus)
                                              kohteet)))))

(defonce paikkauskohteet-kartalla
  (reaction (let [taso @karttataso-paikkauskohteet
                  paikkauskohteet @paikkauskohteet
                  lomakedata @paikkausilmoitus-lomakedata]
              (when (and taso paikkauskohteet)
                (yllapitokohteet/yllapitokohteet-kartalle
                  paikkauskohteet
                  lomakedata)))))
