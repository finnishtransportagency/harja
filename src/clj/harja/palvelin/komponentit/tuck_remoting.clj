(ns harja.palvelin.komponentit.tuck-remoting
  (:require [tuck.remoting :as tr]
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
    ;; TODO: Poista debug-lokitus
    (println "### Tuck-remoting Yhdistetty! Asiakas-id:" client-id)
    (swap! asiakkaat assoc client-id {:e! e!
                                      :kayttaja kayttaja})
    (doseq [hook (vals yhdistaessa-hookit)]
      (hook client)))

  Katkaistu
  (process-event [{tila :tila} {::tr/keys [client-id]} {asiakkaat ::asiakkaat}]
    ;; TODO: Poista debug-lokitus
    (println "### Tuck remoting yhteys asiakkaaseen katkesi. Asiakas-id" client-id)
    (swap! asiakkaat dissoc client-id)))

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
          kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))]
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
              (let [{::tr/keys [event-id] :as msg} (lue-viesti data)
                    ;; TODO: Poista debug-lokitus
                    _ (println "### msg" msg)
                    event (tr/map->event msg)]
                (tr/process-event event {::tr/client-id client-id
                                         ::tr/e! (e!-fn event-id)
                                         :kayttaja kayttaja} konteksti)))))))))

(defrecord TuckRemoting [konteksti-atomi]
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (assoc this ::lopeta
                (http/julkaise-palvelu http :ws
                  (kasittelija
                    (tee-tuck-remoting-kasittelija konteksti-atomi db))
                  {:ring-kasittelija? true
                   :ei-todennettava true})))
  (stop [{stop ::stop http :http-palvelin :as this}]
    (stop)
    (http/poista-palvelu http :ws)
    (dissoc this ::stop)))

(defn luo-tuck-remoting []
  (->TuckRemoting (atom {::asiakkaat (atom {})
                         ::yhdistaessa-hookit {}})))

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
