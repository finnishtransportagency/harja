(ns harja.tyokalut.tuck-remoting
  "Tuck-remoting apurit"
  (:require [taoensso.timbre :as log]
            [tuck.core :as t]
            [tuck.remoting :as t-remoting]))


(defrecord YhdistaWS [yhteys-onnistui-fn])
(defrecord KatkaiseWS[])

(extend-protocol t/Event
  YhdistaWS
  (process-event [{:keys [yhteys-onnistui-fn]} app]
    (if (:ws-yhteys app)
      app
      (assoc app :ws-yhteys
                 (t-remoting/connect!
                   (str "ws://" js/window.location.host "/_/ws?")
                   app
                   #(do
                      (log/info "WebSocket-yhteys aloitettu")
                      (when (fn? yhteys-onnistui-fn)
                        (yhteys-onnistui-fn)))))))

  KatkaiseWS
  (process-event [_ app]
    (log/info "KatkaiseWS: TODO")
    ;; TODO: Tuck-remoting -kirjastossa on puute:
    ;;        Yhteyden katkaisemista varten ei ole toimintoa, vaan mikäli yhteys katkeaa tuck-remoting
    ;;        yrittää jatkuvasti luoda yhteyden uudelleen.
    ;;        Tuck-remoting kirjaston connect! toimintoon pitää automaattinen reconnect tehdä optionaaliseksi
    ;;        ja lisätä disconnect!-toiminto, joka katkaisee yhteyden ja estää automaattisen reconnecting
    ;;        websocketin onclose eventin yhteydessä.
    app))
