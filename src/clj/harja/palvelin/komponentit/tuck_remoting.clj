(ns harja.palvelin.komponentit.tuck-remoting
  (:require [tuck.remoting :as tr]
            [tuck.remoting.server :as server]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http]
            [taoensso.timbre :as log]))

(defrecord Yhdistetty [])
(defrecord Katkaistu [tila])

(extend-protocol tr/ServerEvent
  Yhdistetty
  (process-event [_ {::tr/keys [e! client-id] :as client}
                  {asiakkaat ::asiakkaat
                   yhdistaessa-hookit ::yhdistaessa-hookit}]
    (println "Tuck remoting client yhdistetty! Asiakas-id:" client-id)
    (swap! asiakkaat assoc client-id e!)
    (doseq [hook (vals yhdistaessa-hookit)]
      (hook client)))

  Katkaistu
  (process-event [{tila :tila} {::tr/keys [client-id]} {asiakkaat ::asiakkaat}]
    (println "Tuck remoting yhteys asiakkaaseen katkesi. Asiakas-id" client-id)
    (swap! asiakkaat dissoc client-id)))

(defn kasittelija [tr-kasittelija]
  (fn [req]
    (println "Kutsuttiin ws käsittelijää!" req)
    (try (tr-kasittelija req)
         (catch Exception e
           (log/error e "Poikkeus Tuck-remoting käsittelijässä"))) ) )

(defrecord TuckRemoting [konteksti-atomi]
  component/Lifecycle
  (start [{http :http-palvelin :as this}]
    (assoc this ::lopeta
                (http/julkaise-palvelu http :ws
                  (kasittelija (server/make-handler {:context-fn (fn [req]
                                                                   @konteksti-atomi)
                                                     :on-connect-event ->Yhdistetty
                                                     :on-close-event ->Katkaistu}))
                  {:ring-kasittelija? true
                   :ei-todennettava true}
                  )))
  (stop [{stop ::stop http :http-palvelin :as this}]
    (stop)
    (http/poista-palvelu http :ws)
    (dissoc this ::stop)))

(defn luo-tuck-remoting []
  (->TuckRemoting (atom {::asiakkaat (atom {::yhdistaessa-hookit {}})})))

(defn merge-konteksti! [tuck-remoting konteksti]
  (swap! (:konteksti-atomi tuck-remoting) merge konteksti))

(defn laheta-kaikille!
  [{konteksti-atomi :konteksti-atomi} event]
  (when konteksti-atomi
    (let [asiakkaat @(::asiakkaat @konteksti-atomi)]
      (doseq [e! (vals asiakkaat)]
        (e! event)))))

(defn rekisteroi-yhdistaessa-hook! [{konteksti :konteksti-atomi :as foo} yhdistaessa-fn]
  (let [id (gensym)]
    (println foo)
    (swap! konteksti update ::yhdistaessa-hookit assoc id yhdistaessa-fn)
    #(swap! konteksti update ::yhdistaessa-hookit dissoc id)))
