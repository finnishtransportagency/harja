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
  (go (let [tallennettavat
            (into []
                  ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                  (comp (filter #(not (:poistettu %)))
                        (map #(if-let [nimi (:nimi %)]
                                (let [[_ etu suku] (re-matches #"^ *([^ ]+)( *.*?) *$" nimi)]
                                  (assoc %
                                    :etunimi (str/trim etu)
                                    :sukunimi (str/trim suku)))
                                %)))
                  uudet-yhteyshenkilot)
            poistettavat
            (into []
                  (keep #(when (and (:poistettu %)
                                    (> (:id %) 0))
                           (:id %)))
                  uudet-yhteyshenkilot)
            res (<! (yht/tallenna-urakan-yhteyshenkilot (:id ur) tallennettavat poistettavat))]
        (reset! yhteyshenkilot res)
        true)))

(defn tallenna-paivystajat [ur paivystajat uudet-paivystajat]
  (log "tallenna päivystäjät!")
  (go (let [tallennettavat
            (into []
                  ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                  (comp (filter #(not (:poistettu %)))
                        (map #(if-let [nimi (:nimi %)]
                                (let [[_ etu suku] (re-matches #"^ *([^ ]+)( *.*?) *$" nimi)]
                                  (assoc %
                                    :etunimi (str/trim etu)
                                    :sukunimi (str/trim suku)))
                                %))
                        ;; goog->js date
                        (map #(pvm/muunna-aika-js % :alku :loppu)))
                  uudet-paivystajat)
            poistettavat
            (into []
                  (keep #(when (and (:poistettu %)
                                    (> (:id %) 0))
                           (:id %)))
                  uudet-paivystajat)
            res (<! (yht/tallenna-urakan-paivystajat (:id ur) tallennettavat poistettavat))]
        (reset! paivystajat res)
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
      [{:otsikko "Rooli" :nimi :rooli :tyyppi :kombo :valinnat @yhteyshenkilotyypit :leveys "15%"
        :validoi [[:ei-tyhja  "Anna yhteyshenkilön rooli"]]}
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
       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys "10%"}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys "10%"}
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys "30%"}]
      @yhteyshenkilot
      ] 

     
     [grid/grid
      {:otsikko "Päivystystiedot"
       :tyhja "Ei päivystystietoja."
       :tallenna #(tallenna-paivystajat ur paivystajat %)}
      [{:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                                nimi
                                (str (:etunimi %)
                                     (when-let [suku (:sukunimi %)]
                                       (str " " suku))))
        :aseta (fn [yht arvo]
                 (assoc yht :nimi arvo))
        
        
        :tyyppi :string :leveys "15%"}
       {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys "15%"
        :tyyppi :valinta
        :valinta-arvo :id
        :valinta-nayta #(if % (:nimi %) "- valitse -")
        :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}
       
       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys "10%"}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys "10%"}
       {:otsikko  "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys "15%"}
       {:otsikko "Alkupvm" :nimi :alku :tyyppi :pvm :fmt pvm/pvm :leveys "10%"}
       {:otsikko "Loppupvm" :nimi :loppu :tyyppi :pvm :fmt pvm/pvm :leveys "10%"}
       ]
      @paivystajat
      ]
       
     ]))



