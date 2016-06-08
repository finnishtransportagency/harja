(ns harja-laadunseuranta.utils
  (:require [cljs.core.async :as async :refer [timeout <!]]
            [reagent.core :as reagent]
            [goog.string :as gstr]
            [goog.string.format]
            [reagent.ratom :as ratom])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(defn- tuettu-selain? []
  (some #(re-matches % (clojure.string/lower-case js/window.navigator.userAgent))
        [#".*android.*" #".*chrome.*" #".*ipad.*"]))

(defn- flip [atomi]
  (swap! atomi not))

(defn timed-swap! [delay atom swap-fn]
  (after-delay delay
     (swap! atom swap-fn)))

(defn unreactive-deref [atom]
  (binding [ratom/*ratom-context* nil]
    @atom))

(defn parsi-kaynnistysparametrit [params]
  (let [params (clojure.string/split (subs params 1) "&")
        keys-values (map #(clojure.string/split % "=") params)]
    (into {} keys-values)))

(defn- timestamp []
  (.getTime (js/Date.)))

(defn ipad? []
  (re-matches #".*iPad.*" (.-platform js/navigator)))

(defn erota-mittaukset [havainnot]
  (select-keys havainnot [:lampotila :lumisuus :tasaisuus :kitkamittaus
                          :polyavyys :kiinteys :sivukaltevuus]))

(defn erota-havainnot [havainnot]
  (let [h (dissoc havainnot
                  :lampotila :lumisuus :tasaisuus :kitkamittaus
                  :polyavyys :kiinteys :sivukaltevuus)]
    (filterv h (keys h))))

(defn kahdella-desimaalilla [arvo]
  (gstr/format "%.2f" arvo))

(defn keywordize-map [m]
  (into {}
        (map vector (map keyword (keys m)) (vals m))))

(defn- avg [mittaukset]
  (/ (reduce + 0 mittaukset) (count mittaukset)))
