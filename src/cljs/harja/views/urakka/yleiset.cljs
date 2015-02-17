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
            [clojure.string :as str]
        
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.ui.yleiset :refer [deftk]]))
 



;; hallintayksikkö myös
;; organisaatio = valinta siitä mitä on tietokannassa
;; sampoid

(defn tallenna-yhteyshenkilot [ur yhteyshenkilot uudet-yhteyshenkilot]
  (go (let [data (into []
                       ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                       (comp (map #(if-let [nimi (:nimi %)]
                                     (let [[_ etu suku] (re-matches #"^ *([^ ]+)( *.*?) *$" nimi)]
                                       (assoc %
                                         :etunimi etu
                                         :sukunimi suku))
                                     %)))
                       uudet-yhteyshenkilot)
            
            res (<! (yht/tallenna-urakan-yhteyshenkilot (:id ur) data))]
        (reset! yhteyshenkilot res)
        true)))

(deftk yleiset [ur]
  [yhteyshenkilot (<! (yht/hae-urakan-yhteyshenkilot (:id ur)))
   paivystajat (<! (yht/hae-urakan-paivystajat (:id ur)))
   yhteyshenkilotyypit (<! (yht/hae-yhteyshenkilotyypit))]

  (do
    (log "paivystajat: " (pr-str paivystajat))
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
       :tyhja "Ei yhteyshenkilöitä."
       :tallenna #(tallenna-yhteyshenkilot ur yhteyshenkilot %)}
      [{:otsikko "Rooli" :nimi :rooli :tyyppi :kombo :valinnat @yhteyshenkilotyypit :leveys "15%"}
       {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys "15%"
        :tyyppi :valinta
        :valinta-arvo :id
        :valinta-nayta #(if % (:nimi %) "- valitse -")
        :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}
       
       {:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                                nimi
                                (str (:etunimi %)
                                     (when-let [suku (:sukunimi %)]
                                       (str " " suku))))
        :aseta (fn [yht arvo]
                 (assoc yht :nimi arvo))
        
        
        :tyyppi :string :leveys "15%"}
       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys "15%"}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys "15%"}
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys "20%"}]
      @yhteyshenkilot
      ] 
        
     [grid/grid
      {:otsikko "Päivystystiedot"
       :tyhja "Ei päivystystietoja."}
      [{:otsikko "Rooli" :nimi :rooli :tyyppi :string :leveys "15%"}
       {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys "15%"
        :tyyppi :valinta
        :valinta-arvo :id
        :valinta-nayta #(if % (:nimi %) "- valitse -")
        :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}
       {:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                                nimi
                                (str (:etunimi %)
                                     (when-let [suku (:sukunimi %)]
                                       (str " " suku))))
        :aseta (fn [yht arvo]
                 (assoc yht :nimi arvo))
        
        
        :tyyppi :string :leveys "15%"}
       {:otsikko "Puhelin (virka)" :nimi :puhelin :tyyppi :puhelin :leveys "10%"}
       {:otsikko "Puhelin (gsm)" :nimi :gsm :tyyppi :puhelin :leveys "10%"}
       {:otsikko  "Sähköposti" :nimi :sahkoposti :tyyppin :email :leveys "15%"}]
      @paivystajat
      ]
       
     ]))



