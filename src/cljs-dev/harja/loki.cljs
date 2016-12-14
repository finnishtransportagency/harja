(ns harja.loki
  "Apufunktioita lokittamiseen."
  (:require [clojure.string :refer [join]]))

(def +mittaa-aika+ false)

(defn ajan-mittaus-paalle []
  (set! +mittaa-aika+ true))

(defn maybe-join [messages]
  ;; phantomjs only logs first argument of console.log
  (if js/window._phantom
    (array (join " " messages))
    messages))

(defn warn [& things]
  (.apply js/console.warn js/console (maybe-join (apply array things))))

(defn error [& things]
  (.apply js/console.error js/console (maybe-join (apply array things))))

(defn log [& things]
  (.apply js/console.log js/console (maybe-join (apply array things))))

(defn logt
  "Logita taulukko (console.table), sisääntulevan datan on oltava sekvenssi mäppejä."
  [data]
  (if (aget js/console "table")
    (.table js/console (clj->js data))
    (.log js/console (pr-str data))))

(defn tarkkaile!
  [nimi atomi]
  (add-watch atomi :tarkkailija (fn [_ _ vanha uusi]
                                  (log nimi ": " (pr-str vanha) " => " (pr-str uusi)))))
