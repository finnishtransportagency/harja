(ns harja.tyokalut.tuck-remoting
  "Tuck-remoting apurit"
  (:require [taoensso.timbre :as log]
            [tuck.core :as t]
            [tuck.remoting :as tr]
            [cljs.core.async :refer [<! >! timeout chan alts! pub sub unsub unsub-all put! close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(defrecord YhdistaWS [tila-atom yhteys-onnistui-fn yhteys-katkaistu-fn])
(defrecord KatkaiseWS [])

(defn ws-yhteys? [app]
  (:ws-yhteys app))

;; ---- Tuck-remoting kirjaston parannuksia, TODO: Teen virallisen PR:n myöhemmin tuck-remoteen! (siksi koodi englanniksi)

(def ws-opts {:reconn-interval 4000
              :max-reconn-attempts 20})

(defonce reconnect-state (atom {:timer-id nil
                                :connection-attempts-count 0}))


(defn connect!* [channel ws-url app-atom on-connect on-disconnect reconnect?]
  ;; Prevent creating a new WebSocket object, unless reconnecting
  (when (or (not @tr/connection) reconnect?)
    (let [conn (js/WebSocket. ws-url)]
      (set! (.-onopen conn) (fn [_]
                              (when (fn? on-connect) (on-connect))
                              (put! channel :opened)))
      (set! (.-onmessage conn) (fn [event]
                                 (when event
                                   (tr/receive app-atom event))))
      (set! (.-onclose conn) (fn [event]
                               (.log js/console (str "Tuck-remoting: WebSocket closed. WasClean? " (.-wasClean event)))
                               (when (fn? on-disconnect)
                                 (on-disconnect (.-code event) (.-reason event) (.-wasClean event)))

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
        (cond (= :closed-dirty state)
              (let [timeout-ms (+ (:reconn-interval ws-opts) (* @conn-attempts-count 5000))]
                (.log js/console (str "Tuck-remoting: Trying to reconnect the WebSocket connection in " (/ timeout-ms 1000) " seconds..."))
                (<! (timeout timeout-ms))

                (swap! conn-attempts-count inc)
                (connect!* channel ws-url app-atom on-connect on-disconnect true))

              (or (= :opened state) (= :closed state))
              (reset! conn-attempts-count 0))

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
