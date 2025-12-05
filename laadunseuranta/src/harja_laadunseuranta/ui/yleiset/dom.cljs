(ns harja-laadunseuranta.ui.yleiset.dom
  (:require
    [harja-laadunseuranta.asiakas.tapahtumat :as tapahtumat]
    [reagent.core :as reagent :refer [atom]]
    [cljs.core.async :as async :refer [<! >! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.tiedot.indexeddb-macros :refer [with-transaction with-objectstore with-cursor]]))


(def leveys (atom (-> js/window .-innerWidth)))
(def korkeus (atom (-> js/window .-innerHeight)))

(defn- paivita-leveys! []
  (reset! leveys (-> js/window .-innerWidth)))

(defn- paivita-korkeus! []
  (reset! korkeus (-> js/window .-innerHeight)))

(defn kuuntele-leveyksia []
  (.addEventListener js/window "resize" #(do (paivita-leveys!)
                                             (paivita-korkeus!)
                                             (tapahtumat/julkaise! {:aihe :window-resize}))))
(defn kuuntele-body-klikkauksia []
  (.addEventListener js/document.body "click"
    (fn [e]
      (tapahtumat/julkaise! {:aihe :body-click
                             :tapahtuma e}))
    ;; Note: React 17 Event Delegation muutosten myötä täytyy body-klikkauksia kuunnella
    ;; capture vaiheessa, jotta tapahtuma saadaan kiinni kuuntelijassa.
    ;; React 17 tottelee stopPropagationia, joten tapahtuman kulku estetään ellei capture vaihetta käytetä.
    ;; https://legacy.reactjs.org/blog/2020/08/10/react-v17-rc.html#fixing-potential-issues
    (clj->js {:capture true})))

(defn elementin-etaisyys-viewportin-alareunaan [solmu]
  (let [r (.getBoundingClientRect solmu)
        etaisyys (- @korkeus (.-bottom r))]
    etaisyys))

(defn sisalla?
  "Tarkistaa onko annettu tapahtuma tämän React komponentin sisällä."
  [dom-node tapahtuma]
  (let [elt (.-target tapahtuma)]
    (loop [ylempi (.-parentNode elt)]
      (if (or (nil? ylempi)
              (= ylempi js/document.body))
        false
        (if (= dom-node ylempi)
          true
          (recur (.-parentNode ylempi)))))))

;; Responsiivisen UI:n vakiot
;; (Jos muutat näitä, tarkista myös vakiot.less)

(def +leveys-tabletti+ 760) ; Tätä suurempi laite on tabletti, pienempi puhelin
