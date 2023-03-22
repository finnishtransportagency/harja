(ns harja.tuck-remoting.ilmoitukset-ohjain
  (:require
    [taoensso.timbre :as log]
    [tuck.core :as tuck]
    [harja.tyokalut.tuck-remoting :as tr-tyokalut]
    [harja.tuck-remoting.ilmoitukset-eventit :as eventit]))

(defonce tila-atom (atom {:ilmoitukset []}))

(defrecord YhdistaWS [])

(defn ws-yhteys-onnistui-kasittelija [e!]
  (log/info "Ilmoitukset: WS-yhteys aloitettu. Seurataan uusia ilmoituksia WS:n kautta.")
  (e! (eventit/->KuunteleIlmoituksia {:urakka-id 666})))

(defn ws-yhteys-katkaistu-kasittelija []
  (log/info "Ilmoitukset: WS-yhteys katkaistu. Uusien ilmoitusten seuraaminen lopetettu."))

(extend-protocol tuck/Event
  eventit/Ilmoitus
  (process-event [{opts :opts} app]
    (println "saatiin ilmoitus!!!" opts)
    (update app :ilmoitukset conj opts))

  YhdistaWS
  (process-event [_ app]
    (tuck/action!
      (fn [e!]
        (e! (tr-tyokalut/->YhdistaWS
              tila-atom
              (partial ws-yhteys-onnistui-kasittelija e!)))))))
