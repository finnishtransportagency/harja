(ns harja.testutils
  (:require [cljs.test :as t]
            [cljs-react-test.utils :as rt-utils]
            [dommy.core :as dommy]
            [reagent.core :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >!] :as async])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def *test-container* (atom nil))

(def komponentti-fixture
  {:before #(reset! *test-container* (rt-utils/new-container!))
   :after #(do (rt-utils/unmount! @*test-container*)
               (reset! *test-container* nil))})

(def fake-palvelukutsut (atom nil))

(defn- suorita-fake-palvelukutsu [palvelu parametrit]
  (let [[kanava vastaus-fn] (get @fake-palvelukutsut palvelu)
        ch (async/chan)]
    (if kanava
      (go
        (let [vastaus (vastaus-fn parametrit)]
          (>! ch vastaus)
          (>! kanava vastaus))
        (async/close! kanava)
        (async/close! ch))
      (async/close! ch))
    ch))

(def fake-palvelut-fixture
  {:before #(do (reset! k/testmode suorita-fake-palvelukutsu)
                (reset! fake-palvelukutsut {}))
   :after #(do (reset! k/testmode nil)
               (reset! fake-palvelukutsut {}))})

(defn fake-palvelukutsu [palvelu vastaus-fn]
  (let [ch (async/chan)]
    (swap! fake-palvelukutsut assoc palvelu [ch vastaus-fn])
    ch))

(defn render
  "Renderöi annetun komponentin (hiccup vektori) testi containeriin"
  [component]
  (r/render component @*test-container*))


(defn sel [path]
  (dommy/sel @*test-container* path))

(defn sel1 [path]
  (dommy/sel1 @*test-container* path))

(defn grid-solu
  ([grid-id rivi-nro sarake-nro]
   (grid-solu grid-id rivi-nro sarake-nro ":nth-child(1)"))
  ([grid-id rivi-nro sarake-nro solu-path]
   (sel1 (str "#" grid-id " tbody "
              "tr:nth-child(" (inc rivi-nro) ") "
              "td:nth-child(" (inc sarake-nro) ") "
              solu-path))))
