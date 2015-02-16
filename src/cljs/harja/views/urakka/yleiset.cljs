(ns harja.views.urakka.yleiset
  "Urakan 'Yleiset' välilehti: perustiedot ja yhteyshenkilöt"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.yhteystiedot :as yht]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            
            [cljs.core.async :refer [<!]]
            
        
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.ui.yleiset :refer [deftk]]))
 



;; hallintayksikkö myös
;; organisaatio = valinta siitä mitä on tietokannassa
;; sampoid



(deftk yleiset [ur]
  [yhteyshenkilot (<! (yht/hae-urakan-yhteyshenkilot (:id ur)))
   paivystajat nil
   yhteyshenkilotyypit (<! (yht/hae-yhteyshenkilotyypit nil))]

  (do
    (log "URAKKANI ON: " (pr-str ur), "alku: " (:alkupvm ur))
    [:div
     [bs/panel {}
      "Yleiset tiedot"
      [yleiset/tietoja {}
       "Urakan nimi:" (:nimi ur)
       "Urakan tunnus:" (:sampoid ur)
       "Aikaväli:" [:span.aikavali (pvm/pvm (:alkupvm ur)) " \u2014 " (pvm/pvm (:loppupvm ur))]
       "Tilaaja:" (:nimi (:hallintayksikko ur))
       "Urakoitsija:" (:nimi (:urakoitsija ur))]]
        
     [grid/grid
      {:otsikko "Yhteyshenkilöt"
       :tallenna #(log "TALLENNETAAN: " (pr-str %)) }
      [{:otsikko "Rooli" :nimi :rooli :tyyppi :kombo :valinnat @yhteyshenkilotyypit :leveys "15%"}
       {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys "15%"
        :tyyppi :valinta
        :valinta-arvo :id
        :valinta-nayta #(if % (:nimi %) "- valitse -")
        :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}
       
       {:otsikko "Nimi" :hae #(str (:etunimi %) " " (:sukunimi %)) :tyyppi :string :leveys "15%"}
       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys "15%"}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys "15%"}
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys "20%"}]
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

