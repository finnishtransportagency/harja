(ns harja.testutils.shared-testutils
  "Harjan ja mobiilin laadunseurannan yhteiset jaetut fronttitestityökalut"
  (:require [cljs.test :as t :refer-macros [is]]
            [cljs-react-test.utils :as rt-utils]
            [dommy.core :as dommy]
            [reagent.core :as r]
            [cljs.core.async :refer [<! >! timeout alts!] :as async]
            [cljs-react-test.simulate :as sim]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def *test-container* (atom nil))

(def komponentti-fixture
  {:before #(reset! *test-container* (rt-utils/new-container!))
   :after #(do (rt-utils/unmount! @*test-container*)
               (reset! *test-container* nil))})

(defn render
  "Renderöi annetun komponentin (hiccup vektori) testi containeriin"
  [component]
  (r/render component @*test-container*))


(defn sel [path]
  (dommy/sel @*test-container* path))

(defn sel1 [path]
  (dommy/sel1 @*test-container* path))

(defn paivita
  "Kutsuu reagent flush ja odottaa että render on tapahtunut.
  Palauttaa kanavan, joka suljetaan renderin jälkeen."
  []
  (let [ch (async/chan)]
    (r/flush)
    (r/after-render #(async/close! ch))
    ch))

(defn grid-solu
  ([grid-id rivi-nro sarake-nro]
   (grid-solu grid-id rivi-nro sarake-nro ":nth-child(1)"))
  ([grid-id rivi-nro sarake-nro solu-path]
   (sel1 (str "#" grid-id " tbody "
              "tr:nth-child(" (inc rivi-nro) ") "
              "td:nth-child(" (inc sarake-nro) ") "
              solu-path))))


(defn elt? [o]
  (instance? js/HTMLElement o))

(defn ->elt [element-or-path]
  (if (elt? element-or-path)
    element-or-path
    (let [e (sel1 element-or-path)]
      (is (some? e) (str "Elementtiä polulla " element-or-path " ei löydy!"))
      e)))

(comment
  ;; FIXME: tätä ei saatu dropdown listan kanssa toimimaan.
  ;; Ei testattu muuten, jätetty tähän ettei toista kertaa
  ;; tarvitse aikaa hukata tämän kanssa.
  ;; getClientRects, offsetWidth, jne.. (mitä jquery tekee)
  ;; näyttää olevan aina sama vaikka dropdown olisi kiinni

  (defn- is-hidden? [node]
    (and node
         (let [style (.getComputedStyle js/window node)]
           (or (= "none" (some-> style .-display))
               (= "hidden" (some-> style .-visibility))
               (is-hidden? (.-parentNode node))))))

  (defn visible? [path]
    (let [elt (->elt path)]
      (and elt (not (is-hidden? elt))))))

(defn click [path]
  (let [elt (->elt path)]
    (is (some? elt) (str "Elementti polulla " path " ei ole!"))
    (when elt
      (let [disabled? (.-disabled elt)]
        (is (not disabled?) (str "Elementti " elt " on disabled tilassa!"))
        (when-not disabled?
          (sim/click elt nil))))))

(defn change [path value]
  (let [elt (->elt path)]
    (when elt
      (sim/change elt {:target {:value value}}))))

(defn disabled? [path]
  (let [elt (->elt path)]
    (when elt
      (.-disabled elt))))

(defn enabled? [path]
  (not (disabled? path)))

(defn text [path]
  (when-let [e (->elt path)]
    (.-innerText e)))

(defn ilman-tavutusta [teksti]
  (str/replace teksti #"\u00AD" ""))

(defn blur [path]
  (sim/blur (->elt path) nil))

(defn input? [path]
  (let [elt (->elt path)]
    (when elt
      (= "INPUT" (.-tagName elt)))))

(defn print-html []
  (println (.-innerHTML @*test-container*)))
