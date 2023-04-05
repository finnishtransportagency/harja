(ns harja.palvelin.komponentit.tuck-remoting
  (:require
    [harja.palvelin.komponentit.todennus :as todennus]
    [tuck.remoting :as tr]
    [tuck.remoting.server :as server]
    [org.httpkit.server :refer [with-channel on-close send! on-receive]]
    [com.stuartsierra.component :as component]
    [harja.palvelin.komponentit.http-palvelin :as http]
    [taoensso.timbre :as log]
    [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [hae-kayttaja]]
    [tuck.remoting.transit :as transit]
    [clojure.string :as str])
  (:import (java.util UUID)))

(defrecord Yhdistetty [])
(defrecord Katkaistu [tila])

(extend-protocol tr/ServerEvent
  Yhdistetty
  (process-event [_ {::tr/keys [e! client-id] :keys [kayttaja] :as client}
                  {asiakkaat ::asiakkaat
                   yhdistaessa-hookit ::yhdistaessa-hookit}]
    (swap! asiakkaat assoc client-id {:e! e!
                                      :kayttaja kayttaja})
    (log/info "Tuck-remoting yhdistetty! Asiakas-id:" client-id ", asiakkaiden lukumäärä: " (count @asiakkaat))

    (doseq [hook (vals yhdistaessa-hookit)]
      (hook client)))

  Katkaistu
  (process-event [{tila :tila} {::tr/keys [client-id] :as client} {asiakkaat ::asiakkaat
                                                                   yhteys-poikki-hookit ::yhteys-poikki-hookit}]
    (swap! asiakkaat dissoc client-id)

    (log/info "Tuck remoting yhteys asiakkaaseen katkesi. Asiakas-id" client-id ", asiakkaiden lukumäärä: " (count @asiakkaat))

    (doseq [hook (vals yhteys-poikki-hookit)]
      (hook client))))

(defn kasittelija [tr-kasittelija]
  (fn [req]
    (try (tr-kasittelija req)
         (catch Exception e
           (log/error e "Poikkeus Tuck-remoting käsittelijässä")))))

(defn- lue-viesti [viesti]
  (let [event-mappi (transit/transit->clj viesti)]
    (assert (map? event-mappi) "Saatiin event joka ei ole map")
    event-mappi))

(defn tee-tuck-remoting-kasittelija
  "Laajennettu tuck-remoting.server/make-handler-funktio.
  Lisätty käyttäjän hakeminen headereista ja tallentaminen client-mappiin."
  [konteksti-atomi db]
  (fn tuck-remoting-handler [request]
    (let [konteksti @konteksti-atomi
          client-id (str (UUID/randomUUID))
          ;; TODO: Välitä oikeudet, joilla voi ohittaa OAM_* headerit tietylle käyttäjille
          kayttaja (todennus/koka->kayttajatiedot db (:headers request) nil)]
      ;; https://http-kit.github.io/server.html#websocket
      (with-channel request kanava
        (on-close kanava (fn [status]
                           (tr/process-event (->Katkaistu status)
                             {::tr/client-id client-id
                              ::tr/e! #(throw
                                         (ex-info
                                           "Client connection has been closed"
                                           {::tr/client-id client-id}))}
                             konteksti)))
        (let [e!-fn (fn [event-id]
                      #(send! kanava
                         (transit/clj->transit
                           {::tr/reply-to event-id
                            ::tr/event-type (str/replace (.getName (type %)) \_ \-)
                            ::tr/event-args (into {} %)})))]
          (tr/process-event (->Yhdistetty)
            {::tr/client-id client-id
             ::tr/e! (e!-fn nil)
             :kayttaja kayttaja}
            konteksti)
          (on-receive kanava
            (fn [data]
              (let [{::tr/keys [event-id event-type] :as msg} (lue-viesti data)]
                ;; TODO: Poista debug-lokitus
                #_(println "### tuck-remoting, received msg:" msg)
                ;; Handle ping/pong heartbeat-events outside normal Tuck-event handling
                (if (= :ping event-type)
                  (send! kanava (transit/clj->transit {::tr/event-type :pong}))
                  (let [event (tr/map->event msg)]
                    (tr/process-event event {::tr/client-id client-id
                                             ::tr/e! (e!-fn event-id)
                                             :kayttaja kayttaja} konteksti)))))))))))

(defrecord TuckRemoting [konteksti-atomi]
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (assoc this ::lopeta
                (http/julkaise-palvelu http :ws
                  (kasittelija
                    (tee-tuck-remoting-kasittelija konteksti-atomi db))
                  {:ring-kasittelija? true
                   :ei-todennettava true})))
  (stop [{lopeta ::lopeta http :http-palvelin :as this}]
    (lopeta)
    (http/poista-palvelu http :ws)
    (dissoc this ::lopeta)))

(defn luo-tuck-remoting []
  (->TuckRemoting (atom {::asiakkaat (atom {})
                         ::yhdistaessa-hookit {}
                         ::yhteys-poikki-hookit {}})))

(defn merge-konteksti! [tuck-remoting konteksti]
  (swap! (:konteksti-atomi tuck-remoting) merge konteksti))

(defn laheta-kaikille!
  [{konteksti-atomi :konteksti-atomi} event]
  (when konteksti-atomi
    (let [asiakkaat @(::asiakkaat @konteksti-atomi)]
      (doseq [{e! :e!} (vals asiakkaat)]
        (e! event)))))

(defn rekisteroi-yhdistaessa-hook! [{konteksti :konteksti-atomi} yhdistaessa-fn]
  (let [id (gensym)]
    (swap! konteksti update ::yhdistaessa-hookit assoc id yhdistaessa-fn)
    #(swap! konteksti update ::yhdistaessa-hookit dissoc id)))

(defn rekisteroi-yhteys-poikki-hook! [{konteksti :konteksti-atomi} yhteys-poikki-fn]
  (let [id (gensym)]
    (swap! konteksti update ::yhteys-poikki-hookit assoc id yhteys-poikki-fn)
    #(swap! konteksti update ::yhteys-poikki-hookit dissoc id)))
