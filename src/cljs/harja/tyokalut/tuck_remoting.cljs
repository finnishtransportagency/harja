(ns harja.tyokalut.tuck-remoting
  "Tuck-remoting apurit"
  (:require
    [harja.asiakas.kommunikaatio :as k]
    [taoensso.timbre :as log]
    [cljs.core.async :refer [<! >! timeout chan alts! pub sub unsub unsub-all put! close!]]
    [tuck.core :as t]
    [tuck.remoting :as tr]
    [tuck.remoting.transit :as transit])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(defrecord YhdistaWS [tila-atom yhteys-onnistui-fn yhteys-katkaistu-fn])
(defrecord KatkaiseWS [])

(defn ws-yhteys? [app]
  (:ws-yhteys-kanava app))

;; ---- Tuck-remoting kirjaston parannuksia, TODO: Teen virallisen PR:n myöhemmin tuck-remoteen! (siksi koodi englanniksi)

(declare disconnect!)

(def ws-opts {:reconn-interval-ms (* 4 1000)
              ;:max-reconn-attempts 20
              :heartbeat-interval-ms (* 15 1000)
              ;; Timeout set to 60 seconds in case Chrome throttles timers aggressively.
              ;; In that case, Chrome will run timers every minute. So, the heartbeat timeout must be minimum 60s.
              :heartbeat-timeout-ms (* 60 1000)})

;; 3004 - A custom close code that we use to indicate a need to reconnect after disconnect
;; https://www.iana.org/assignments/websocket/websocket.xml#close-code-number
(def closed-unclean-code 3004)
(def closed-clean-code 1000)
(def heartbeat-state (atom {:timer-id nil
                            :timeout-timer-id nil}))

(defn start-heartbeat-timeout-timer! []
  (let [timer-id (js/setTimeout
                   (fn []
                     ;; Disconnect and a request connection restart
                     (disconnect! closed-unclean-code))
                   (:heartbeat-timeout-ms ws-opts))]
    (swap! heartbeat-state assoc :timeout-timer-id timer-id)))

(defn send-heartbeat! []
  ;; Stop timeout timer if a new ping heartbeat is about to be sent
  (when (:timeout-timer-id @heartbeat-state)
    (js/clearTimeout (:timeout-timer-id @heartbeat-state)))

  (let [conn @tr/connection
        timer-id (js/setTimeout
                   (fn []
                     ;; Send a heartbeat only if connection is alive and not in closed/closing state
                     (when (and conn (not (#{(.-CLOSED conn) (.-CLOSING conn)} (.-readyState conn))))
                       (.send conn (transit/clj->transit {:tuck.remoting/event-type :ping}))
                       (start-heartbeat-timeout-timer!)))
          (:heartbeat-interval-ms ws-opts))]
    (swap! heartbeat-state assoc :timer-id timer-id)))

(defn stop-heartbeat! []
  (let [state @heartbeat-state]
    (when (:timer-id state)
      (js/clearTimeout (:timer-id state)))
    (when (:timeout-timer-id @heartbeat-state)
      (js/clearTimeout (:timeout-timer-id @heartbeat-state)))

    (reset! heartbeat-state {:timer-id nil
                             :timeout-timer-id nil})))

(defn connect!* [channel ws-url app-atom on-connect on-disconnect reconnect?]
  ;; Prevent creating a new WebSocket object, unless reconnecting
  (when (or (not @tr/connection) reconnect?)
    (let [conn (js/WebSocket. ws-url)]
      (.info js/console "Tuck-remoting: Starting a WebSocket connection to: " ws-url "...")

      (set! (.-onopen conn) (fn [_]
                              ;; Stop any ongoing heartbeat timer and reset the heartbeat-state
                              (stop-heartbeat!)
                              ;; Send the first ping-heartbeat on connect
                              (send-heartbeat!)

                              (when (fn? on-connect) (on-connect))
                              (put! channel :opened)))
      (set! (.-onmessage conn) (fn [event]
                                 (when event
                                   ;; Handling ping/pong heartbeat-events outside normal Tuck-event processing
                                   (let [event-data (transit/transit->clj (.-data event))]
                                     ;; If recieved a :pong response from the server, respond with another ping
                                     ;;  after :heartbeat-interval-ms ms.
                                     (if (= :pong (:tuck.remoting/event-type event-data))
                                       (send-heartbeat!)
                                       ;; Otherwise, handle a normal Tuck-event
                                       (tr/receive app-atom event))))))
      (set! (.-onclose conn) (fn [event]
                               (.info js/console (str "Tuck-remoting: WebSocket closed. ("
                                                   (str "Code: " (.-code event))
                                                   (when (boolean? (.-wasClean event))
                                                     (str ", WasClean?: " (.-wasClean event)))
                                                   (when (= (.-code event) closed-unclean-code)
                                                    ", Heartbeat timeout?: true") ")"))
                               (when (fn? on-disconnect)
                                 (on-disconnect
                                   (.-code event) (.-reason event)
                                   ;; Check if the connection was closed cleanly (wasClean can be null in some cases)
                                   (or (.-wasClean event) (= (.-code event) closed-clean-code))))

                               ;; Stop the heartbeat timer and reset the heartbeat-state
                               (stop-heartbeat!)

                               ;; Trigger reconnect (:closed-dirty) if close was unclean or a custom close code was used
                               (put! channel (if (and (not (= closed-unclean-code (.-code event)))
                                                   ;; For some reason wasClean can be null, so the closing code much be also checked.
                                                   (or (.-wasClean event) (= closed-clean-code (.-code event))))
                                               :closed
                                               :closed-dirty))))
      (reset! tr/connection conn))))

(def connection-channel (atom nil))

(defn connect! [ws-url app-atom on-connect on-disconnect]
  (let [channel (reset! connection-channel (chan))
        conn-attempts-count (atom 0)]
    (connect!* channel ws-url app-atom on-connect on-disconnect false)

    (go-loop []
      (let [state (<! channel)]
        (when (or (= :opened state) (= :closed state))
          (reset! conn-attempts-count 0))

        (when (= :closed-dirty state)
          (let [timeout-ms (+ (:reconn-interval-ms ws-opts) (* @conn-attempts-count 5000))
                _ (.info js/console (str "Tuck-remoting: Trying to reconnect the WebSocket connection in " (/ timeout-ms 1000) " seconds..."))
                [state _] (alts! [channel (timeout timeout-ms)])]

            ;; State returns :closed before timeout finished, cancel the ongoing reconnection attempt.
            (if-not (= :closed state)
              (do
                (swap! conn-attempts-count inc)
                (connect!* channel ws-url app-atom on-connect on-disconnect true))
              (.info js/console (str "Tuck-remoting: Reconnection canceled.")))))

        (when-not (= :closed state)
          (recur))))
    channel))

(defn disconnect!
  ([] (disconnect! 1000))
  ([close-code]
   (let [conn @tr/connection
         ;; 1000 - normal closure
         ;; https://www.iana.org/assignments/websocket/websocket.xml#close-code-number
         close-code (if (int? close-code) close-code 1000)]
     (when conn
       (.info js/console "Tuck-remoting: Disconnecting the WebSocket connection...")
       (.close conn close-code)
       (reset! tr/connection nil))

     ;; If there is ongoing reconnection timout waiting, put :closed in the channel if disconnecting normally (code = 1000).
     ;; This will cancel the ongoing reconnection timer and prevents unwanted reconnection loop.
     ;; TODO: Check if this logic can be done better.
     ;;       I'm not sure if the go-loop solution for reconnections is the best option.
     (when (and @connection-channel (= 1000 close-code))
       (put! @connection-channel :closed)))))

(defn ^:export test-ws-disconnect [code]
  (disconnect! code))

;; ---- END --- Tuck-remoting kirjaston parannuksia,

(extend-protocol t/Event
  YhdistaWS
  (process-event [{:keys [tila-atom yhteys-onnistui-fn yhteys-katkaistu-fn]} app]
    (if (ws-yhteys? app)
      (do
        (log/info "WS-yhteys on jo toiminnassa. Käytetään vanhaa yhteyttä.")
        app)
      (assoc-in app [:ws-yhteys-kanava]
        (connect!
          ;; Käytetään localhost-kehityksessä WS-protokollaa ja muualla WSS
          ;; Testipalvelimilla (ja tuotannossa) pitäisi olla normaalisti HTTPS-sertifikaatti käytössä, joten
          ;; WSS-protokollaa pitää käyttää niissä.
          (let [protokolla (if (k/kehitysymparistossa-localhost?) "ws://" "wss://")]
            (str protokolla js/window.location.host "/_/ws?"))
          tila-atom
          yhteys-onnistui-fn
          yhteys-katkaistu-fn))))

  KatkaiseWS
  (process-event [_ app]
    ;; Käytetään omaa implementaatiota disconnect! toiminnallisuudesta.
    ;; Pyritään toteuttamaan tämä tuck-remoting kirjastoon myöhemmin + muita parannuksia, mitä tarvitaan.
    (disconnect!)
    (dissoc app :ws-yhteys-kanava)))
