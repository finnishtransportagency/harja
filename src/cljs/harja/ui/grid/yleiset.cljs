(ns harja.ui.grid.yleiset
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log tarkkaile! logt] :refer-macros [mittaa-aika]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje vihje] :as y]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo vain-luku-atomina]]
            [harja.ui.validointi :as validointi]
            [harja.ui.skeema :as skeema]
            [goog.events :as events]
            [goog.events.EventType :as EventType]

            [cljs.core.async :refer [<! put! chan]]
            [clojure.string :as str]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid.protokollat :refer
             [Grid vetolaatikko-auki? sulje-vetolaatikko!
              muokkauksessa-olevat-gridit seuraava-grid-id
              avaa-vetolaatikko! muokkaa-rivit! otsikko?
              lisaa-rivi! vetolaatikko-rivi vetolaatikon-tila
              aseta-grid +rivimaara-jonka-jalkeen-napit-alaskin+]]
            [harja.ui.dom :as dom]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [cljs-time.core :as t]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.makrot :refer [fnc]]
                   [harja.tyokalut.ui :refer [for*]]))

(defn tayta-tiedot-alas
  "Täyttää rivin tietoja alaspäin."
  [rivit s lahtorivi tayta-fn]
  (let [tayta-fn (or tayta-fn

                     ;; Oletusfunktio kopioi tiedon sellaisenaan
                     (let [nimi (:nimi s)
                           lahtoarvo ((or (:hae s) nimi) lahtorivi)
                           aseta (or (:aseta s)
                                     (fn [rivi arvo]
                                       (assoc rivi nimi arvo)))]
                       (fn [_ taytettava]
                         (aseta taytettava lahtoarvo))))]
    (loop [uudet-rivit (list)
           alku false
           [rivi & rivit] rivit]
      (if-not rivi
        uudet-rivit
        (if (= lahtorivi rivi)
          (recur (conj uudet-rivit rivi) true rivit)
          (if-not alku
            (recur (conj uudet-rivit rivi) false rivit)
            (recur (conj uudet-rivit
                         (tayta-fn lahtorivi rivi))
                   true
                   rivit)))))))

(defn- tayta-alas-nappi [{:keys [fokus tayta-alas fokus-id arvo tulevat-rivit hae s ohjaus rivi]}]
  (when (and (= fokus fokus-id)
             (tayta-alas arvo)

             ;; Sallitaan täyttö, vain jos tulevia rivejä on ja kaikkien niiden arvot ovat tyhjiä
             (not (empty? tulevat-rivit))
             (every? str/blank? (map hae tulevat-rivit)))
    [:div {:class (if (= :oikea (:tasaa s))
                    "pull-left"
                    "pull-right")}
     [:div {:style {:position "absolute" :display "inline-block"}}
      [:button {:class (str "nappi-toissijainen nappi-tayta" (when (:kelluta-tayta-nappi s) " kelluta-tayta-nappi"))
                :title (:tayta-tooltip s)
                :style {:position "absolute"
                        :left (when (= :oikea (:tasaa s)) 0)
                        :right (when-not (= :oikea (:tasaa s)) "100%")}
                :on-click #(muokkaa-rivit! ohjaus tayta-tiedot-alas [s rivi (:tayta-fn s)])}
       (ikonit/livicon-arrow-down) " Täytä"]]]))