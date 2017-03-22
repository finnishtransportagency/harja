(ns harja.tiedot.urakka.paallystys
  "Päällystyksen tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.tiedot.urakka.yllapitokohteet.muut-kustannukset :as muut-kustannukset]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka :as urakka]
    [harja.domain.tierekisteri :as tr-domain]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.tiedot.urakka.yllapito :as yllapito-tiedot])

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
  (reaction (let [tienumero @yllapito-tiedot/tienumero]
              (when @paallystysilmoitukset
                (filterv #(or (nil? tienumero)
                              (= (:tr-numero %) tienumero))
                         @paallystysilmoitukset)))))

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
                  kohteet (when yllapitokohteet
                            (yllapitokohteet/suodata-yllapitokohteet-tienumerolla yllapitokohteet tienumero))]
              kohteet)))

(def yhan-paallystyskohteet
  (reaction
    (let [kohteet @yllapitokohteet-suodatettu
          yhan-paallystyskohteet (when kohteet
                                   (yllapitokohteet/suodata-yllapitokohteet-tyypin-ja-yhan-mukaan
                                     kohteet true :paallystys))]
      (tr-domain/jarjesta-kohteiden-kohdeosat yhan-paallystyskohteet))))

(def harjan-paikkauskohteet
  (reaction
    (let [kohteet @yllapitokohteet-suodatettu
          harjan-paikkauskohteet (when kohteet
                                   (yllapitokohteet/suodata-yllapitokohteet-tyypin-ja-yhan-mukaan
                                     kohteet false :paikkaus))]
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
   :valinta-nayta (fn [rivi muokattava?]
                    (if rivi
                      (str (:lyhenne rivi) " - " (:nimi rivi))
                      (if muokattava?
                        "- Valitse päällyste -"
                        "")))
   :valinnat paallystys-ja-paikkaus/+paallystetyypit+})

(def raekoko-grid-skeema
  {:otsikko "Rae\u00ADkoko" :nimi :raekoko :tyyppi :numero :desimaalien-maara 0
   :tasaa :oikea
   :validoi [[:rajattu-numero nil 0 99]]})

(def tyomenetelma-grid-skeema
  {:otsikko "Pääll. työ\u00ADmenetelmä"
   :nimi :tyomenetelma
   :tyyppi :valinta
   :valinta-arvo :koodi
   :valinta-nayta (fn [rivi muokattava?]
                    (if rivi
                      (str (:lyhenne rivi) " - " (:nimi rivi))
                      (if muokattava?
                        "- Valitse menetelmä -"
                        "")))
   :valinnat pot/+tyomenetelmat+})