(ns harja.views.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [ajax-loader vihje]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.grid :refer [grid]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(def hakutiedot (atom nil))

(tarkkaile! "[YHA] Hakutiedot " hakutiedot)

(defn hae-yha-urakat [hakuparametrit]
  ;; TODO
  (log "[YHA] Suoritetaan YHA-haku")
  (go []))

(def hakutulokset
  (reaction<! [hakutiedot @hakutiedot]
              {:nil-kun-haku-kaynnissa? true
               :odota 500}
              (hae-yha-urakat hakutiedot)))

(defn- sido-yha-urakka-harja-urakkaan [yha-urakka harja-urakka]
  ;; TODO
  (log "[YHA] Sidotaan YHA-urakka Harja-urakkaan..."))

(defn- hakutulokset []
  [grid
   {:otsikko "Löytyneet urakat"
    :tyhja (if (nil? @hakutulokset) [ajax-loader "Haetaan..."] "Urakoita ei löytynyt")}
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
   @hakutulokset])

(defn- hakutiedot []
  [lomake {:otsikko "Urakan tiedot"
           :muokkaa! (fn [uusi-data]
                       (reset! hakutiedot uusi-data))}
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
    @hakutiedot]])

(defn- tuontidialogi []
  (log "[YHA] Render dialog tiedoilla:" (pr-str @hakutiedot))
  [:div
   [vihje "Urakka tätyy sitoa YHA:n vastaavaan urakkaan tietojen siirtämiseksi Harjaan. Etsi YHA-urakka ja tee sidonta."]
   [hakutiedot]
   [hakutulokset]])

(defn nayta-tuontidialogi []
  (modal/nayta!
    {:otsikko "Urakan sitominen YHA-urakkaan"
     :luokka "yha-tuonti"
     :footer [:button.nappi-toissijainen {:on-click (fn [e]
                                                      (.preventDefault e)
                                                      (modal/piilota!))}
              "Sulje"]}
    (tuontidialogi)))