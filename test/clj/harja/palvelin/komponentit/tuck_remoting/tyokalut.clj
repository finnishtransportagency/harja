(ns harja.palvelin.komponentit.tuck-remoting.tyokalut
  (:require [clojure.string :as str]
            [java-http-clj.websocket :as ws]
            [taoensso.timbre :as log]
            [harja.testi :refer [portti]]
            [tuck.remoting :as tr]
            [tuck.remoting.transit :as transit]))

(def event-id (atom 0))

(def ws-client-yhteydet-atom
  "Kokoelma WS-client yhteysobjekteja"
  (atom {}))

(def ws-client-yhteys-vastaukset-atom
  "Saadut vastaukset ws-yhteyden id:llä varustettuna"
  (atom {}))

(defn websocket-client [id oam-headerit]
  (let [ws-url (str "ws://" "localhost:" portti "/_/ws?")]
    (log/info "Yhdistetään WS: " ws-url)

    (ws/build-websocket
      ws-url
      {:on-text (fn [ws vastaus last?]
                  (log/info (str "WS-asiakas " id " sai vastauksen:" vastaus (transit/transit->clj vastaus)))
                  (swap! ws-client-yhteys-vastaukset-atom assoc id (transit/transit->clj vastaus)))
       :on-error (fn [ws throwable]
                   (log/error (str "WS-asiakas " id " sai virheen: ") (.getMessage throwable)))}
      {:headers oam-headerit})))

(defn luo-ws-yhteys! [id oam-headerit]
  (when id
    (swap! ws-client-yhteydet-atom assoc id (websocket-client id oam-headerit))))

(defn ws-yhteys [id]
  (get-in @ws-client-yhteydet-atom [id]))

(defn sulje-ws-yhteys! [id]
  (when (get-in @ws-client-yhteydet-atom [id])
    (ws/close (get-in @ws-client-yhteydet-atom [id]))
    (swap! ws-client-yhteydet-atom dissoc id)))

(defn sulje-kaikki-ws-yhteydet! []
  (when (seq @ws-client-yhteydet-atom)
    (doseq [yhteys (vals @ws-client-yhteydet-atom)]
      (ws/close yhteys))))

(defn ws-vastaus [id]
  (when id
    (get-in @ws-client-yhteys-vastaukset-atom [id])))

(defn siivoa-ws-vastaus! [id]
  (swap! ws-client-yhteys-vastaukset-atom dissoc id))

(defn websocket-fixture
  "Fixture tuck-remoting ws-testien yhteyteen. Varmistaa, että testissä käytetyt yhteydet
  varmasti suljateen, ja että vastauksien väliaikaiseen säilömiseen käytetty atom siivotaan
  testien välillä."
  [testit]
  (reset! ws-client-yhteys-vastaukset-atom {})

  (testit)
  ;; Jos ws-yhteyksiä on päällä testin jälkeen, varmista että ne suljetaan
  (when (seq @ws-client-yhteydet-atom)
    (doseq [yhteys (vals @ws-client-yhteydet-atom)]
      (ws/close yhteys)))

  (reset! ws-client-yhteydet-atom {}))



(defn eventin-konstruktori->tyyppi [tuck-event]
  (str/replace (.getName (type tuck-event)) \_ \-))

(defn asiakas-e!
  "Lähettää tuck-remoten server-eventin, kuten selaimessa toimiva CLJS WS-client sen lähettäisi"
  [ws-yhteys]
  (fn [tuck-server-event]
    (ws/send
      ws-yhteys
      (transit/clj->transit
        {::tr/event-id (swap! event-id inc)
         ::tr/event-type (eventin-konstruktori->tyyppi tuck-server-event)
         ::tr/event-args (into {} tuck-server-event)}))))
