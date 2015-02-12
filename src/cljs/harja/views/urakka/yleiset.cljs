(ns harja.views.urakka.yleiset
  "Urakan 'Yleiset' välilehti: perustiedot ja yhteyshenkilöt"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.yhteystiedot :as yht]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.ui.yleiset :refer [deftk]]))


(deftk yleiset [ur]
  [yhteyshenkilot (<! (yht/hae-urakan-yhteyshenkilot (:id ur)))
   paivystajat nil]

  (do  
    (log "urakka-yleiset: " yhteyshenkilot)
    [:div
     "Urakan tunnus: foo" [:br]
     "Aikaväli: 123123" [:br]
     "Hallintayksikkö: sehän näkyy jo murupolussa" [:br]
     "Urakoitsija: Urakkapojat Oy" [:br]
       
     [grid/grid
      {:otsikko "Yhteyshenkilöt"}
      [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
       {:otsikko "Organisaatio" :hae #(get-in % [:organisaatio :nimi]) :tyyppi :string}
       {:otsikko "Nimi" :hae #(str (:etunimi %) " " (:sukunimi %)) :tyyppi :string}
       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :string}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :string} ;; mieti eri tyyppejä :puhelin / :email / jne...
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email}]
      @yhteyshenkilot
      ]
       
     [grid/grid
      {:otsikko "Päivystystiedot"}
      [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
       {:otsikko "Organisaatio" :nimi :organisaatio :tyyppi :string}
       {:otsikko "Nimi" :nimi :nimi :tyyppi :string}
       {:otsikko "Puhelin (virka)" :nimi :puhelin :tyyppi :string}
       {:otsikko "Puhelin (gsm)" :nimi :gsm :tyyppi :string} ;; mieti eri tyyppejä :puhelin / :email / jne...
       {:otsikko  "Sähköposti" :nimi :sahkoposti :tyyppin :email}]
      @paivystajat
      ]
       
     ]))



(comment
(defn yleiset
  "Yleiset välilehti"
  [ur]
  (let [paivita (fn [this]
                  (let [{:keys [yhteyshenkilot paivystajat]} (reagent/state this)]
                    (go
                      (log "haetaan urakan henkilöitä: " (:id ur))
                      (let [henkilot (<! (yht/hae-urakan-yhteyshenkilot (:id ur)))]
                        (log "urakan henkilöt: " (pr-str henkilot))
                        (reset! yhteyshenkilot henkilot ;(vec (filter #(= (:rooli %) :yhteyshenkilo)))
                                )))))]
                  
    (reagent/create-class
     {:display-name "urakka-yleiset"
      :get-initial-state
      (fn [this] {:yhteyshenkilot (atom nil)
                  :paivystajat (atom nil)})

      :component-did-mount
      (fn [this]
        (paivita this))
      :reagent-render
      (fn [ur]
        (let [{:keys [yhteyshenkilot paivystajat]} (reagent/state (reagent/current-component))]
          (log "urakka-yleiset: " (pr-str yhteyshenkilot))
          [:div
           "Urakan tunnus: foo" [:br]
           "Aikaväli: 123123" [:br]
           "Hallintayksikkö: sehän näkyy jo murupolussa" [:br]
           "Urakoitsija: Urakkapojat Oy" [:br]
       
           [grid/grid
            {:otsikko "Yhteyshenkilöt"}
            [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
             {:otsikko "Organisaatio" :hae #(get-in % [:organisaatio :nimi]) :tyyppi :string}
             {:otsikko "Nimi" :hae #(str (:etunimi %) " " (:sukunimi %)) :tyyppi :string}
             {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :string}
             {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :string} ;; mieti eri tyyppejä :puhelin / :email / jne...
             {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email}]
            @yhteyshenkilot
            ]
       
           [grid/grid
            {:otsikko "Päivystystiedot"}
            [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
             {:otsikko "Organisaatio" :nimi :organisaatio :tyyppi :string}
             {:otsikko "Nimi" :nimi :nimi :tyyppi :string}
             {:otsikko "Puhelin (virka)" :nimi :puhelin :tyyppi :string}
             {:otsikko "Puhelin (gsm)" :nimi :gsm :tyyppi :string} ;; mieti eri tyyppejä :puhelin / :email / jne...
             {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppin :email}]
            @paivystajat
            ]
       
           ]))}))))

