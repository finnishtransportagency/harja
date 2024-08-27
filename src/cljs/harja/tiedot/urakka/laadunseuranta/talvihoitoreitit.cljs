(ns harja.tiedot.urakka.laadunseuranta.talvihoitoreitit
  (:require [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [taoensso.timbre :as log])
  (:require-macros
    [reagent.ratom :refer [reaction]]))


(def karttataso-talvihoitoreitit (atom []))
(defonce karttataso-nakyvissa? (atom true))

(defn talvihoitoreitit-kartalla []
  (reaction
    (let [_ (js/console.log "talvihoitoreitit-kartalla" )]
      (when @karttataso-nakyvissa?
        nil))))


(defrecord HaeTalvihoitoreitit [])
(defrecord HaeTalvihoitoreititOnnistui [vastaus])
(defrecord HaeTalvihoitoreititEpaonnistui [vastaus])


(extend-protocol tuck/Event

  HaeTalvihoitoreitit
  (process-event
    [_ app]
    (log/debug "HaeTalvihoitoreitit")

    app)

  HaeTalvihoitoreititOnnistui
  (process-event
    [{:keys [vastaus]} app]
    (log/debug "HaeTalvihoitoreititOnnistui :: vastaus" (pr-str vastaus))
    app)

  HaeTalvihoitoreititEpaonnistui
  (process-event
    [{:keys [vastaus]} app]
    (log/debug "HaeTalvihoitoreititEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  )
