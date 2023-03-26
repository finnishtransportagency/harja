(ns harja.tyokalut.tuck-remoting
  "Tuck-remoting apurit"
  (:require [taoensso.timbre :as log]
            [cljs.core.async :refer [<! >! timeout chan alts! pub sub unsub unsub-all put! close!]]
            [tuck.core :as t]
            [tuck.remoting :as tr]
            [tuck.remoting.transit :as transit])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(defrecord YhdistaWS [tila-atom yhteys-onnistui-fn yhteys-katkaistu-fn])
(defrecord KatkaiseWS [])

(defn ws-yhteys? [app]
  (:ws-yhteys app))

;; ---- Tuck-remoting kirjaston parannuksia, TODO: Teen virallisen PR:n myöhemmin tuck-remoteen! (siksi koodi englanniksi)

(def ws-opts {:reconn-interval 4000
              :max-reconn-attempts 20
              :heartbeat-interval 5000
              :heartbeat-timeout 10000})

(def heartbeat-state (atom {:timer-id nil
                            :timestamp nil}))

(defn send-heartbeat! []
  (let [conn @tr/connection
        timer-id (js/setTimeout
                   (fn []
                     (when conn
                       (.send conn (transit/clj->transit {:tuck.remoting/event-type :ping}))
                       (swap! heartbeat-state assoc :timestamp (js/Date.now))))
          (:heartbeat-interval ws-opts))]
    (swap! heartbeat-state assoc :timer-id timer-id)))

(defn stop-heartbeat! []
  (let [state @heartbeat-state]
    (when (:timer-id state)
      (js/clearTimeout (:timer-id state))
      (reset! heartbeat-state {:timer-id nil
                               :timestamp nil}))))

(defn connect!* [channel ws-url app-atom on-connect on-disconnect reconnect?]
  ;; Prevent creating a new WebSocket object, unless reconnecting
  (when (or (not @tr/connection) reconnect?)
    (let [conn (js/WebSocket. ws-url)]
      (set! (.-onopen conn) (fn [_]
                              (when (fn? on-connect) (on-connect))
                              ;; Send the first ping-heartbeat on connect
                              (send-heartbeat!)

                              (put! channel :opened)))
      (set! (.-onmessage conn) (fn [event]
                                 (when event
                                   ;; Handling ping/pong heartbeat-events outside normal Tuck-event processing
                                   (let [event-data (transit/transit->clj (.-data event))]
                                     ;; If receivied a :pong response from the server, respond with another ping
                                     ;;  after :heartbeat-interval ms.
                                     (if (= :pong (:tuck.remoting/event-type event-data))
                                       (send-heartbeat!)
                                       ;; Otherwise, handle a normal Tuck-event
                                       (tr/receive app-atom event))))))
      (set! (.-onclose conn) (fn [event]
                               (.log js/console (str "Tuck-remoting: WebSocket closed. WasClean? " (.-wasClean event)))
                               (when (fn? on-disconnect)
                                 (on-disconnect (.-code event) (.-reason event) (.-wasClean event)))

                               ;; Stop the heartbeat timer and reset the heartbeat-state
                               (stop-heartbeat!)

                               (put! channel (if-not (.-wasClean event)
                                               :closed-dirty
                                               :closed))))
      (reset! tr/connection conn))))

(defn connect! [ws-url app-atom on-connect on-disconnect]
  (let [channel (chan)
        conn-attempts-count (atom 0)]
    (connect!* channel ws-url app-atom on-connect on-disconnect false)

    (go-loop []
      (let [state (<! channel)]
        (when (or (= :opened state) (= :closed state))
          (reset! conn-attempts-count 0))

        (when (= :closed-dirty state)
              (let [timeout-ms (+ (:reconn-interval ws-opts) (* @conn-attempts-count 5000))]
                (.log js/console (str "Tuck-remoting: Trying to reconnect the WebSocket connection in " (/ timeout-ms 1000) " seconds..."))
                (<! (timeout timeout-ms))

                (swap! conn-attempts-count inc)
                (connect!* channel ws-url app-atom on-connect on-disconnect true)))

        (when-not (= :closed state)
          (recur))))))

(defn disconnect! []
  (let [conn @tr/connection]
    (when conn
      ;; 1000 - normal closure
      ;; https://www.iana.org/assignments/websocket/websocket.xml#close-code-number
      (.close conn 1000)
      (reset! tr/connection nil))))

;; ---- END --- Tuck-remoting kirjaston parannuksia,

(extend-protocol t/Event
  YhdistaWS
  (process-event [{:keys [tila-atom yhteys-onnistui-fn yhteys-katkaistu-fn]} app]
    (if (ws-yhteys? app)
      (do
        ;; TODO: Poista debug-lokitus
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
    ;; Käytetään omaa implementaatiota disconnect! toiminnallisuudesta.
    ;; Pyritään toteuttamaan tämä tuck-remoting kirjastoon myöhemmin + muita parannuksia, mitä tarvitaan.
    (disconnect!)
    (dissoc app :ws-yhteys)))
