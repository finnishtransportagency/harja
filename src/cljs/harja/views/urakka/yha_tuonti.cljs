(ns harja.views.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [ajax-loader vihje]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.lomake :refer [lomake]]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.ui.grid :refer [grid]]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn- hakulomake []
  [lomake {:otsikko "Urakan tiedot"
           :muokkaa! (fn [uusi-data]
                       (reset! yha/hakulomake-data uusi-data))}
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
    @yha/hakulomake-data]])

(defn- hakutulokset [urakka]
  [grid
   {:otsikko "Löytyneet urakat"
    :tyhja (if (nil? @yha/hakutulokset-data) [ajax-loader "Haetaan..."] "Urakoita ei löytynyt")}
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
                     {:on-click #(yha/sido-yha-urakka-harja-urakkaan (:id urakka) rivi)}
                     "Valitse"])}]
   @yha/hakutulokset-data])

(defn- tuontidialogi [urakka]
  (log "[YHA] Render dialog tiedoilla:" (pr-str @yha/hakulomake-data))
  [:div
   [vihje "Urakka tätyy sitoa YHA:n vastaavaan urakkaan tietojen siirtämiseksi Harjaan. Etsi YHA-urakka ja tee sidonta."]
   [hakulomake]
   [hakutulokset urakka]])

(defn nayta-tuontidialogi [urakka]
  (modal/nayta!
    {:otsikko "Urakan sitominen YHA-urakkaan"
     :luokka "yha-tuonti"
     :footer [:button.nappi-toissijainen {:on-click (fn [e]
                                                      (.preventDefault e)
                                                      (modal/piilota!))}
              "Sulje"]}
    (tuontidialogi urakka)))