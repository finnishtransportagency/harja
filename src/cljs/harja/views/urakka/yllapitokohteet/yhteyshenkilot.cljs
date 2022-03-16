(ns harja.views.urakka.yllapitokohteet.yhteyshenkilot
  (:require [reagent.core :refer [atom]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.views.urakka.yleiset :refer [urakkaan-liitetyt-kayttajat]]
            [harja.ui.modal :as modal]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.urakka :as u]
            [harja.ui.viesti :as viesti]
            [clojure.string :as str]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction-writable]]))

(def yhteyshenkilot (atom nil)) ; nil = haetaan, vector = tulos, :virhe = epäonnistui

(defn- yhteyshenkilot-taulukko [yhteyshenkilot]
  [grid/grid
   {:otsikko "Yhteyshenkilöt"
    :tyhja "Ei yhteyshenkilöitä."}
   [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
    {:otsikko "Nimi" :nimi :nimi :tyyppi :string
     :hae #(str (:etunimi %) " " (:sukunimi %))}
    {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin}
    {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin}
    {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email}]
   yhteyshenkilot])

(defn- toolitip-siirry-paallytysurakkaan
  [onko-oikeus?]
  (if onko-oikeus?
    "Siirry päällystysurakkaan"
    "Roolillasi ei ole oikeutta siirtyä päällystysurakkaan."))

(defn- urakan-nimi-ja-linkki [id nimi oikeus-paallystysurakkaan?]
  [:div.yhteystiedot-urakka
   [:div.fontti-16 "Urakka"]
   [yleiset/wrap-if true
    [yleiset/tooltip {} :% (toolitip-siirry-paallytysurakkaan oikeus-paallystysurakkaan?)]
    [yleiset/linkki nimi #(when oikeus-paallystysurakkaan?
                            (nav/valitse-urakka-id! id))]]])

(defn- yhteyshenkilot-view [yhteyshenkilot-atom]
  (fn [yhteyshenkilot-atom]
    (let [{:keys [fim-kayttajat yhteyshenkilot
                  yhteysurakan-id yhteysurakan-nimi
                  oikeus-paallystysurakkaan?] :as tiedot} @yhteyshenkilot-atom]
      (if tiedot
        (if (= tiedot :virhe)
          [:p "Virhe yhteyshenkilöiden haussa."]
          [:div
           (when (and yhteysurakan-id yhteysurakan-nimi)
             [urakan-nimi-ja-linkki yhteysurakan-id yhteysurakan-nimi oikeus-paallystysurakkaan?])
           [urakkaan-liitetyt-kayttajat fim-kayttajat]
           [yhteyshenkilot-taulukko yhteyshenkilot]])
        [ajax-loader "Haetaan yhteyshenkilöitä..."]))))

(defn nayta-yhteyshenkilot-modal!
  "Urakkatyyppi on joko :paallystys tai :tiemerkinta, riippuen siitä kumman urakan
   yhteyshenkilöitä halutaan tarkastella"
  [{:keys [yllapitokohde-id urakkatyyppi yhteysurakan-id yhteysurakan-nimi oikeus-paallystysurakkaan?]}]
  (go (do
        (reset! yhteyshenkilot nil)
        (let [vastaus (<! (k/post! :yllapitokohteen-urakan-yhteyshenkilot {:yllapitokohde-id yllapitokohde-id
                                                                           :urakkatyyppi urakkatyyppi}))]
          (if (k/virhe? vastaus)
            (do
              (viesti/nayta! "Virhe haettaessa yhteyshenkilöitä!" :warning)
              (reset! yhteyshenkilot :virhe))
            (reset! yhteyshenkilot (assoc vastaus :yhteysurakan-id yhteysurakan-id
                                                  :yhteysurakan-nimi yhteysurakan-nimi
                                                  :oikeus-paallystysurakkaan? oikeus-paallystysurakkaan?))))))

  (modal/nayta!
    {:otsikko (str "Kohteen "
                   (str/lower-case (u/urakkatyyppi->otsikko urakkatyyppi))
                   "urakan yhteyshenkilöt")
     :footer [napit/sulje #(modal/piilota!)]}
    [yhteyshenkilot-view yhteyshenkilot]))

(defn- paikkauskohteen-yhteyshenkilot-view [yhteyshenkilot-atom]
  (fn [yhteyshenkilot-atom]
    (let [yhteyshenkilot @yhteyshenkilot-atom]
      (if yhteyshenkilot
        (if (= yhteyshenkilot :virhe)
          [:p "Virhe yhteyshenkilöiden haussa."]
          [:div
           [yhteyshenkilot-taulukko yhteyshenkilot]])
        [ajax-loader "Haetaan yhteyshenkilöitä..."]))))

(defn nayta-paikkauskohteen-yhteyshenkilot-modal!
  "Paikkauskohteella on erilaiset yhteyshenkilöt kuin ylläpitokohteella. Joten siksi oma fn"
  [urakka-id]
  (go (do
        (reset! yhteyshenkilot nil)
        (let [vastaus (<! (k/post! :hae-paikkauskohteen-yhteyshenkilot urakka-id))]
          (if (k/virhe? vastaus)
            (do
              (viesti/nayta! "Virhe haettaessa yhteyshenkilöitä!" :warning)
              (reset! yhteyshenkilot :virhe))
            (reset! yhteyshenkilot vastaus)))))

  (modal/nayta!
    {:leveys "80%"
     :otsikko (str "Paikkauskohteen yhteyshenkilöt")
     :footer [napit/sulje #(modal/piilota!)]}
    [paikkauskohteen-yhteyshenkilot-view yhteyshenkilot]))
