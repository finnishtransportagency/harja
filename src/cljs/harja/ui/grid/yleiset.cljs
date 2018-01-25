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
  [rivit sarake lahtorivi tayta-fn]
  (let [tayta-fn (or tayta-fn

                     ;; Oletusfunktio kopioi tiedon sellaisenaan
                     (let [nimi (:nimi sarake)
                           lahtoarvo ((or (:hae sarake) nimi) lahtorivi)
                           aseta (or (:aseta sarake)
                                     (fn [rivi arvo]
                                       (assoc rivi nimi arvo)))]
                       (fn [_ taytettava]
                         (aseta taytettava lahtoarvo))))
        tulos (loop [uudet-rivit (list)
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
                             rivit)))))]
    ;; Palautetaan rivit alkuperäisessä järjestyksessä
    (reverse tulos)))

(defn- tayta-alas-nappi [{:keys [fokus tayta-alas fokus-id arvo
                                 tulevat-rivit hae sarake ohjaus rivi]}]
  (when (and (= fokus fokus-id)
             (tayta-alas arvo)

             ;; Sallitaan täyttö, vain jos tulevia rivejä on ja kaikkien niiden arvot ovat tyhjiä
             (not (empty? tulevat-rivit))
             (every? str/blank? (map hae tulevat-rivit)))
    (let [napin-sijainti (cond
                           ;; Asemoi kutsujan mukaan
                           (:tayta-sijainti sarake) (:tayta-sijainti sarake)
                           ;; Useampi kuin yksi nappi, asemoi ylös
                           (and (:tayta-alas? sarake) (:tayta-alas-toistuvasti? sarake)) :ylos
                           ;; Muuten piirretään kentän sisään
                           :default :sisalla)]
      [:div {:class (if (= :oikea (:tasaa sarake))
                      "pull-left"
                      "pull-right")}
       [:div {:style {:position "absolute" :display "flex"}}
        [napit/yleinen-toissijainen "Täytä"
         #(muokkaa-rivit! ohjaus tayta-tiedot-alas [sarake rivi (:tayta-fn sarake)])
         {:title (:tayta-tooltip sarake)
          :luokka (str "nappi-tayta " (when (:kelluta-tayta-nappi sarake) " kelluta-tayta-nappi"))
          :style (case napin-sijainti
                   :ylos
                   {:transform "translateY(-100%)"}
                   ;; Oletuksena sijainti on kentän sisällä
                   :sisalla
                   {:position "absolute"
                    :left (when (= :oikea (:tasaa sarake)) 0)
                    :right (when-not (= :oikea (:tasaa sarake)) "100%")})
          :ikoni (ikonit/livicon-arrow-down)}]
        [napit/yleinen-toissijainen "Toista"
         ;; TODO Toiminnallisuus ei toimi näin
         #(muokkaa-rivit! ohjaus tayta-tiedot-alas [sarake rivi (:tayta-fn sarake)])
         {:title "Toista edelliset rivit alla oleville riveille."
          :luokka (str "nappi-tayta " (when (:kelluta-tayta-nappi sarake) " kelluta-tayta-nappi"))
          :style (case napin-sijainti
                   :ylos
                   {:transform "translateY(-100%)"}
                   :sisalla
                   {:position "absolute"
                    :left (when (= :oikea (:tasaa sarake)) 0)
                    :right (when-not (= :oikea (:tasaa sarake)) "100%")})
          :ikoni (ikonit/livicon-arrow-down)}]]])))