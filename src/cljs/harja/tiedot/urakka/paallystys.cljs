(ns harja.tiedot.urakka.paallystys
  "Päällystyksen tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.tiedot.urakka.paallystys-muut-kustannukset :as muut-kustannukset]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka :as urakka]
    [harja.domain.tierekisteri :as tr-domain]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.sopimus :as sopimus-domain]
    [harja.domain.urakka :as urakka-domain]
    [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
    [harja.ui.viesti :as viesti])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(def kohdeluettelossa? (atom false))
(def paallystysilmoitukset-nakymassa? (atom false))

(defn hae-paallystysilmoitukset [urakka-id sopimus-id vuosi]
  (k/post! :urakan-paallystysilmoitukset {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :vuosi vuosi}))

(defn hae-paallystysilmoitus-paallystyskohteella [urakka-id yllapitokohde-id]
  (k/post! :urakan-paallystysilmoitus-paallystyskohteella {:urakka-id urakka-id
                                                           :paallystyskohde-id yllapitokohde-id}))

(defn tallenna-paallystysilmoitus! [{:keys [urakka-id sopimus-id vuosi lomakedata]}]
  (k/post! :tallenna-paallystysilmoitus {:urakka-id urakka-id
                                         :sopimus-id sopimus-id
                                         :vuosi vuosi
                                         :paallystysilmoitus lomakedata}))

(def paallystysilmoitukset
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @urakka/valittu-sopimusnumero
               nakymassa? @paallystysilmoitukset-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (hae-paallystysilmoitukset valittu-urakka-id valittu-sopimus-id vuosi))))

(def paallystysilmoitukset-suodatettu
  (reaction (let [tienumero @yllapito-tiedot/tienumero
                  kohdenumero @yllapito-tiedot/kohdenumero]
              (when @paallystysilmoitukset
                (yllapitokohteet/suodata-yllapitokohteet @paallystysilmoitukset {:tienumero tienumero
                                                                                 :kohdenumero kohdenumero})))))

(defonce paallystysilmoitus-lomakedata (atom nil))

(defonce karttataso-paallystyskohteet (atom false))

(def yllapitokohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @urakka/valittu-sopimusnumero
               nakymassa? @kohdeluettelossa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (yllapitokohteet/hae-yllapitokohteet valittu-urakka-id valittu-sopimus-id vuosi))))

(def yllapitokohteet-suodatettu
  (reaction (let [tienumero @yllapito-tiedot/tienumero
                  yllapitokohteet @yllapitokohteet
                  kohdenumero @yllapito-tiedot/kohdenumero
                  kohteet (when yllapitokohteet
                            (yllapitokohteet/suodata-yllapitokohteet yllapitokohteet {:tienumero tienumero
                                                                                      :kohdenumero kohdenumero}))]
              kohteet)))

(def yhan-paallystyskohteet
  (reaction-writable
    (let [kohteet @yllapitokohteet-suodatettu
          yhan-paallystyskohteet (when kohteet
                                   (yllapitokohteet/suodata-yllapitokohteet
                                     kohteet
                                     {:yha-kohde? true :yllapitokohdetyotyyppi :paallystys}))]
      (tr-domain/jarjesta-kohteiden-kohdeosat yhan-paallystyskohteet))))

(def harjan-paikkauskohteet
  (reaction-writable
    (let [kohteet @yllapitokohteet-suodatettu
          harjan-paikkauskohteet (when kohteet
                                   (yllapitokohteet/suodata-yllapitokohteet
                                     kohteet
                                     {:yha-kohde? false :yllapitokohdetyotyyppi :paikkaus}))]
      (tr-domain/jarjesta-kohteiden-kohdeosat harjan-paikkauskohteet))))

(def kaikki-kohteet
  (reaction (concat @yhan-paallystyskohteet @harjan-paikkauskohteet (when muut-kustannukset/kohteet
                                                                      @muut-kustannukset/kohteet))))

(defonce paallystyskohteet-kartalla
  (reaction (let [taso @karttataso-paallystyskohteet
                  paallystyskohteet @yhan-paallystyskohteet
                  lomakedata @paallystysilmoitus-lomakedata]
              (when (and taso paallystyskohteet)
                (yllapitokohteet/yllapitokohteet-kartalle
                  paallystyskohteet
                  lomakedata)))))

(defonce kohteet-yha-lahetyksessa (atom nil))

;; Yhteiset UI-asiat

(def paallyste-grid-skeema
  {:otsikko "Päällyste"
   :nimi :paallystetyyppi
   :tyyppi :valinta
   :valinta-arvo :koodi
   :valinta-nayta (fn [rivi]
                    (if (:koodi rivi)
                      (str (:lyhenne rivi) " - " (:nimi rivi))
                      (:nimi rivi)))
   :valinnat paallystys-ja-paikkaus/+paallystetyypit-ja-nil+})

(def raekoko-grid-skeema
  {:otsikko "Rae\u00ADkoko" :nimi :raekoko :tyyppi :numero :desimaalien-maara 0
   :tasaa :oikea
   :validoi [[:rajattu-numero nil 0 99]]})

(def tyomenetelma-grid-skeema
  {:otsikko "Pääll. työ\u00ADmenetelmä"
   :nimi :tyomenetelma
   :tyyppi :valinta
   :valinta-arvo :koodi
   :valinta-nayta (fn [rivi]
                    (if (:koodi rivi)
                      (str (:lyhenne rivi) " - " (:nimi rivi))
                      (:nimi rivi)))
   :valinnat pot/+tyomenetelmat-ja-nil+})

(defn tallenna-paallystysilmoitusten-takuupvmt [paallystysilmoitukset]
  (k/post! :tallenna-paallystysilmoitusten-takuupvmt
           {::pot/tallennettavat-paallystysilmoitusten-takuupvmt paallystysilmoitukset}))