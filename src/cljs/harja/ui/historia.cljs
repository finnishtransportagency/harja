(ns harja.ui.historia
  "Komponentti, joka ylläpitää historiaa ja näyttää kumoa napin"
  (:require [reagent.core :as r]
            [harja.ui.yleiset :as y]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [goog.events.EventType :as event-type]))

(defprotocol Historia
  (voi-kumota? [this] "Tarkistaa voiko historiaa kumota (ei ole tyhjä)")
  (kumoa! [this] "Kumoa yksi muutos")
  (muutos! [this aiempi-tila]
    "Nauhoita yksi muutos")

  (kuuntele! [this]
    "Aloittaa historiaseurannan. Palauttaa funktion, jota kutsumalla kuuntelu lopetetaan."))

(defrecord HistoriaAtom [historia tila-atom ^:mutable kumotaan?]
  Historia
  (voi-kumota? [this]
    (not (empty? @historia)))

  (kumoa! [this]
    (set! kumotaan? true)
    (when (voi-kumota? this)
      (let [aiempi-tila (peek @historia)]
        (swap! historia pop)
        (reset! tila-atom aiempi-tila)))
    (set! kumotaan? false))

  (muutos! [_ aiempi-tila]
    (when-not (= (peek @historia) aiempi-tila)
      (swap! historia conj aiempi-tila)))

  (kuuntele! [this]
    (let [key (gensym "historiawatch")]
      (add-watch tila-atom key
                 (fn [_ _ aiempi-tila _]
                   (when-not kumotaan?
                     (muutos! this aiempi-tila))))
      #(remove-watch tila-atom key))))

(defn historia
  "Luo uuden historian, joka seuraa annetun atomin tilaa"
  [tila-atom]
  (->HistoriaAtom (r/atom []) tila-atom false))

(defn kumoa
  "Kumoa nappi"
  [historia]
  (komp/luo
   (komp/dom-kuuntelija js/window event-type/KEYDOWN
                        (fn [event]
                          (when (and (= 90 (.-keyCode event))
                                     (or (.-ctrlKey event)
                                         (.-metaKey event))
                                     (voi-kumota? historia))
                            (.stopPropagation event)
                            (.preventDefault event)
                            (kumoa! historia))))
   (fn [historia]
     [:button.nappi-toissijainen.kumoa-nappi
      {:disabled (not (voi-kumota? historia))
       :on-click #(do (.stopPropagation %)
                      (.preventDefault %)
                      (kumoa! historia))}
      [ikonit/ikoni-ja-teksti [ikonit/kumoa] " Kumoa"]])))
