(ns harja.tuck-remoting.ilmoitukset-ohjain
  (:require [tuck.core :as t]
            [tuck.remoting :as tr]
            [harja.tuck-remoting.ilmoitukset-eventit :as eventit]))

(defonce tila (atom {:ilmoitukset []}))



(defrecord YhdistaWS [])

(extend-protocol t/Event
  eventit/Ilmoitus
  (process-event [{opts :opts} app]
    (println "saatiin ilmoitus!!!" opts)
    (update app :ilmoitukset conj opts))

  YhdistaWS
  (process-event [_ app]
    (if (:ws-yhteys app)
      app
      (assoc app :ws-yhteys
                 (tr/connect!
                   (str "ws://" js/window.location.host "/_/ws")
                   tila
                   #(println "yhdistetty!")
                   )
                 )
      )
    )

  )
