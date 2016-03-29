(ns harja.ui.dom
  "Yleisiä apureita DOMin ja selaimen hallintaan"
  (:require [reagent.core :as r]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn sisalla?
  "Tarkistaa onko annettu tapahtuma tämän React komponentin sisällä."
  [komponentti tapahtuma]
  (let [dom (r/dom-node komponentti)
        elt (.-target tapahtuma)]
    (loop [ylempi (.-parentNode elt)]
      (if (or (nil? ylempi)
              (= ylempi js/document.body))
        false
        (if (= dom ylempi)
          true
          (recur (.-parentNode ylempi)))))))


(def ie? (let [ua (-> js/window .-navigator .-userAgent)]
           (or (not= -1 (.indexOf ua "MSIE "))
               (not= -1 (.indexOf ua "Trident/"))
               (not= -1 (.indexOf ua "Edge/")))))

(def ei-tuettu-ie? (let [ua (-> js/window .-navigator .-userAgent)
                           ie-versio (.indexOf ua "MSIE ")]
                       (and
                         (not= -1 ie-versio)
                         (<= 10 ie-versio))))

(defonce korkeus (atom (-> js/window .-innerHeight)))
(defonce leveys (atom (-> js/window .-innerWidth)))

(defonce ikkunan-koko
         (reaction [@leveys @korkeus]))

(defn- ikkunan-koko-muuttunut [& _]
  (t/julkaise! {:aihe :ikkunan-koko-muuttunut :leveys @leveys :korkeus @korkeus}))

(defonce ikkunan-koko-tapahtuman-julkaisu
  (do (add-watch korkeus ::ikkunan-koko-muuttunut ikkunan-koko-muuttunut)
      (add-watch leveys ::ikkunan-koko-muuttunut ikkunan-koko-muuttunut)
      true))

(defonce koon-kuuntelija (do (set! (.-onresize js/window)
                                   (fn [_]
                                     (reset! korkeus (-> js/window .-innerHeight))
                                     (reset! leveys (-> js/window .-innerWidth))
                                     ))
                             true))

(defn elementti-idlla [id]
  (.getElementById js/document (name id)))

(defn sijainti
  "Laskee DOM-elementin sijainnin, palauttaa [x y w h]."
  [elt]
  (assert elt (str "Ei voida laskea sijaintia elementille null"))
  (let [r (.getBoundingClientRect elt)
        sijainti [(.-left r) (.-top r) (- (.-right r) (.-left r)) (- (.-bottom r) (.-top r))]]
    sijainti))

(defn offset-korkeus [elt]
  (loop [offset (.-offsetTop elt)
         parent (.-offsetParent elt)]
    (if (or (nil? parent)
            (= js/document.body parent))
      offset
      (recur (+ offset (.-offsetTop parent))
             (.-offsetParent parent)))))


(defn sijainti-sailiossa
  "Palauttaa elementin sijainnin suhteessa omaan säiliöön."
  [elt]
  (let [[x1 y1 w1 h1] (sijainti elt)
        [x2 y2 w2 h2] (sijainti (.-parentNode elt))]
    [(- x1 x2) (- y1 y2) w1 h1]))

(defn elementin-etaisyys-alareunaan [solmu]
  (let [r (.getBoundingClientRect solmu)
        etaisyys (- @korkeus (.-bottom r))]
    etaisyys))

(defn elementin-etaisyys-ylareunaan [solmu]
  (let [r (.getBoundingClientRect solmu)
        etaisyys (.-top r)]
    etaisyys))

(defn elementin-etaisyys-oikeaan-reunaan [solmu]
  (let [r (.getBoundingClientRect solmu)
        etaisyys (- @leveys (.-right r))]
    etaisyys))
