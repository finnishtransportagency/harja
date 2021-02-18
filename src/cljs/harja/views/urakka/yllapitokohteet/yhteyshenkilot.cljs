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
            [harja.domain.roolit :as roolit]
            [clojure.string :as str]
            [harja.ui.napit :as napit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction-writable]]))

(def yhteyshenkilot (atom nil)) ; nil = haetaan, vector = tulos, :virhe = epäonnistui

(defn- yhteyshenkilot-view [yhteyshenkilot-atom]
  (fn [yhteyshenkilot-atom]
    (let [{:keys [fim-kayttajat yhteyshenkilot] :as tiedot} @yhteyshenkilot-atom]
      (if tiedot
        (if (= tiedot :virhe)
          [:p "Virhe yhteyshenkilöiden haussa."]
          [:div
           [urakkaan-liitetyt-kayttajat fim-kayttajat]
           [grid/grid
            {:otsikko "Yhteyshenkilöt"
             :tyhja "Ei yhteyshenkilöitä."}
            [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
             {:otsikko "Nimi" :nimi :nimi :tyyppi :string
              :hae #(str (:etunimi %) " " (:sukunimi %))}
             {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin}
             {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin}
             {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email}]
            yhteyshenkilot]])
        [ajax-loader "Haetaan yhteyshenkilöitä..."]))))

(defn nayta-yhteyshenkilot-modal!
  "Urakkatyyppi on joko :paallystys tai :tiemerkinta, riippuen siitä kumman urakan
   yhteyshenkilöitä halutaan tarkastella"
  [yllapitokohde-id urakkatyyppi]
  (go (do
        (reset! yhteyshenkilot nil)
        (let [vastaus (<! (k/post! :yllapitokohteen-urakan-yhteyshenkilot {:yllapitokohde-id yllapitokohde-id
                                                                           :urakkatyyppi urakkatyyppi}))]
          (if (k/virhe? vastaus)
            (do
              (viesti/nayta! "Virhe haettaessa yhteyshenkilöitä!" :warning)
              (reset! yhteyshenkilot :virhe))
            (reset! yhteyshenkilot vastaus)))))

  (modal/nayta!
    {:leveys "80%"
     :otsikko (str "Kohteen "
                   (str/lower-case (u/urakkatyyppi->otsikko urakkatyyppi))
                   "urakan yhteyshenkilöt")
     :footer [napit/sulje #(modal/piilota!)]}
    [yhteyshenkilot-view yhteyshenkilot]))
