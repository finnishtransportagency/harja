(ns harja.palvelin.komponentit.asetusrekisteri
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [org.httpkit.client :as http]
            [compojure.core :refer [GET]]
            [taoensso.timbre :as log]
            [clojure.string :as str]))


(defn lataa-lokiasetukset! [db]
  (let [lokiasetukset [[".*foo.*" :info]
                       [".*bar.*" :error]]]
    (for [[rx taso] lokiasetukset]
      [(re-pattern rx) taso])))

(defn asenna-lokimiddleware! [ db]
  (let [muutokset (lataa-lokiasetukset! db)
        lokimiddleware (fn [{:keys [hostname message args level] :as ap-args}]
                         (println "lokimiddleware kutsuttu" ap-args)
                         (let [alkup-level level
                               viesti (or message (str (first args)) "")
                               uusi-level (first (filter
                                                  some? (for [[regexp uusi-taso] muutokset]
                                                          (when (re-find regexp viesti)
                                                            uusi-taso))))]
                           (assoc ap-args :level (or uusi-level alkup-level))))]
    (log/set-config! [:middleware]
                     [lokimiddleware])))

(defrecord Asetusrekisteri []
  component/Lifecycle
  (start [{db :db :as this}]
    this)

  (stop [{db :db :as this}]
    this))
