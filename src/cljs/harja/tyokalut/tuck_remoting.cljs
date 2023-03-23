(ns harja.tyokalut.tuck-remoting
  "Tuck-remoting apurit"
  (:require [taoensso.timbre :as log]
            [tuck.core :as t]
            [tuck.remoting :as tr]))


(defrecord YhdistaWS [tila-atom yhteys-onnistui-fn yhteys-katkaistu-fn])
(defrecord KatkaiseWS[])

(defn ws-yhteys? [app]
  (:ws-yhteys app))

;; ---- Tuck-remoting kirjaston parannuksia, teen PR:n myöhemmin
(defn connect! [ws-url app-atom on-connect on-disconnect]
  (let [conn (js/WebSocket. ws-url)]
    (set! (.-onopen conn) (when (fn? on-connect) on-connect))
    (set! (.-onmessage conn) (fn [event]
                               (when event
                                 (tr/receive app-atom event))))
    (set! (.-onclose conn) (fn [event]
                             (when (fn? on-disconnect)
                               (on-disconnect (.-code event) (.-reason event) (.-wasClean event)))

                             ;; Try to reconnect if the connection was not cleanly closed
                             (when-not (.-wasClean event)
                               (.log js/console "WebSocket closed, reconnecting.")
                               (connect! ws-url app-atom nil on-disconnect))))
    (reset! tr/connection conn)))

(defn disconnect! []
  (let [conn @tr/connection]
    (when conn
      ;; 1000 - normal closure
      ;; https://www.iana.org/assignments/websocket/websocket.xml#close-code-number
      (.close conn 1000)
      (reset! tr/connection nil))))

;; ----

(extend-protocol t/Event
  YhdistaWS
  (process-event [{:keys [tila-atom yhteys-onnistui-fn yhteys-katkaistu-fn]} app]
    (if (ws-yhteys? app)
      (do
        (println "WS yhteys on jo aktiivinen, palautetaan vain app")
        app)
      (assoc app :ws-yhteys
                 (connect!
                   (str "ws://" js/window.location.host "/_/ws?")
                   tila-atom
                   yhteys-onnistui-fn
                   yhteys-katkaistu-fn))))

  KatkaiseWS
  (process-event [_ app]
    ;; TODO: Tuck-remoting -kirjastossa on puute:
    ;;        Yhteyden katkaisemista varten ei ole toimintoa, vaan mikäli yhteys katkeaa tuck-remoting
    ;;        yrittää jatkuvasti luoda yhteyden uudelleen.
    ;;        Tuck-remoting kirjaston connect! toimintoon pitää automaattinen reconnect tehdä optionaaliseksi
    ;;        ja lisätä disconnect!-toiminto, joka katkaisee yhteyden ja estää automaattisen reconnecting
    ;;        websocketin onclose eventin yhteydessä.

    ;; Käytetään omaa implementaatiota disconnect! toiminnallisuudesta.
    ;; Pyritään toteuttamaan tämä tuck-remoting kirjastoon myöhemmin + muita parannuksia, mitä tarvitaan.
    (disconnect!)
    (dissoc app :ws-yhteys)))
