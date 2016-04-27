(ns harja.views.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.grid :refer [grid]]))

(def hakutiedot (atom nil))
(def hakutulokset (atom []))

(defn- sido-yha-urakka-harja-urakkaan [yha-urakka harja-urakka]
  ;; TODO
  (log "[YHA] Sidotaan YHA-urakka Harja-urakkaan..."))

(defn- tuontidialogi [optiot]
  [:div
   (when (:kehotus-sitoa? optiot)
     [:div "Urakka tätyy sitoa YHA:n vastaavaan urakkaan tietojen siirtämiseksi Harjaan. Etsi YHA-urakka ja tee sidonta."])
   [lomake {:otsikko "Urakan tiedot"}
    [{:otsikko "Nimi"
      :nimi :nimi
      :pituus-max 512
      :tyyppi :string}
     {:otsikko "Tunniste"
      :nimi :tunniste
      :pituus-max 512
      :tyyppi :string}
     {:otsikko "Vuosi"
      :nimi :vuosi
      :pituus-max 512
      :tyyppi :positiivinen-numero}
     @hakutiedot]]

   [grid
    {:otsikko     "Löytyneet urakat"
     :tyhja       (if (nil? @hakutulokset) [ajax-loader "Haetaan..."] "Urakoita ei löytynyt")}
    [{:otsikko "Tunnus"
      :nimi :tunnus
      :tyyppi :string
      :muokattava? (constantly false)}
     {:otsikko "Nimi"
      :nimi :nimi
      :tyyppi :string
      :muokattava? (constantly false)}
     {:otsikko "ELYt"
      :nimi :elyt
      :tyyppi :string
      :muokattava? (constantly false)}
     {:otsikko "Vuodet"
      :nimi :vuodet
      :tyyppi :string
      :muokattava? (constantly false)}
     {:otsikko "Sidonta"
      :nimi :valitse
      :tyyppi :komponentti
      :komponentti (fn [rivi]
                     [:button.nappi-ensisijainen.nappi-grid
                      {:on-click #(sido-yha-urakka-harja-urakkaan nil nil)}
                        "Valitse"])}]
    @hakutulokset]])

(defn nayta-tuontidialogi []
  (modal/nayta!
    {:otsikko "Urakan sitominen YHA-urakkaan"}
    (tuontidialogi {:kehotus-sitoa? true})))