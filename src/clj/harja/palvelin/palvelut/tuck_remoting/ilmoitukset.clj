(ns harja.palvelin.palvelut.tuck-remoting.ilmoitukset
  "Ilmoitusten Tuck-remoting palvelu. Tämä käsittelee ilmoitusten WS server-eventit, joita lähetetään
  client-puolelta WebSocketin yli.
  Palvelu lähettää myös WS client-eventtejä palvelimelta clientille WebSocketin yli."
  (:require
    [harja.geo :as geo]
    [harja.kyselyt.konversio :as konversio]
    [taoensso.timbre :as log]
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as tuck-remoting]
    [harja.tuck-remoting.ilmoitukset-eventit :as eventit]
    [com.stuartsierra.component :as component]
    [harja.kyselyt.tieliikenneilmoitukset :as tieliikenneilmoitus-q]
    [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot])
  ;; Käytetyt server-side eventit pitää lisäksi importata, jotta compile menee läpi
  (:import (harja.tuck_remoting.ilmoitukset_eventit KuunteleIlmoituksia LopetaIlmoitustenKuuntelu)))

(defn laheta-ilmoitus! [e! opts]
  (log/info "Lähetetään uusi ilmoitus (WS): " opts)

  (e! (eventit/->Ilmoitus opts)))

(defonce kuuntelijat (atom {}))

;; TODO: Tutki voiko harja.transit read/write optioita ottaa käyttöön tuck-remoten transit-toteutuksessa.
(defn muodosta-ilmoitus-vastaus
  "Harja.transit read/write optiot eivät ole käytössä tuck-remoten omassa transit-toteutuksessa, joten muunnetaan
  ilmoitus-vastaus sellaiseksi, että se menee tuck-remoten läpi clientille."
  [ilmoitus]
  (-> ilmoitus
    (update-in [:sijainti] #(when % (geo/pg->clj %)))
    (update-in [:selitteet] #(when % (konversio/pgarray->vector %)))))

(defn hae-ilmoitus [db ilmoitus-id]
  (let [ilmoitus (first (tieliikenneilmoitus-q/hae-ilmoitukset-ilmoitusidlla db [ilmoitus-id]))]
    (muodosta-ilmoitus-vastaus ilmoitus)))


(defn kuuntele-ilmoituksia [db e! kayttaja client-id opts]
  (let [kuuntelija (if (:urakka-id opts)
                     (notifikaatiot/kuuntele-urakan-ilmoituksia (:urakka-id opts)
                       (fn [{ilmoitus-id :payload}]
                         (let [ilmoitus (hae-ilmoitus db ilmoitus-id)]
                           (println "### Kuuntele urakan ilmoituksia, ilmoitus:" (pr-str ilmoitus))

                           (laheta-ilmoitus! e! ilmoitus))))

                     (notifikaatiot/kuuntele-kaikkia-ilmoituksia
                       ;; TODO: Filtteröi ilmoituksen lähetys käyttäjän ja optioiden perusteella (Jeren kommentti)
                       (fn [{ilmoitus-id :payload}]
                         (let [ilmoitus (hae-ilmoitus db ilmoitus-id)]
                           (println "### Kuuntele kaikkia ilmoituksia, ilmoitus:" (pr-str ilmoitus))

                           (laheta-ilmoitus! e! ilmoitus)))))]
    (swap! kuuntelijat assoc client-id {:kayttaja kayttaja
                                        :e! e!
                                        :opts opts
                                        :lopeta-fn kuuntelija})))

(defn lopeta-ilmoitusten-kuuntelu []
  (println "### todo"))

(defrecord IlmoituksetWS []
  component/Lifecycle
  (start [{tuck-remoting :tuck-remoting db :db :as this}]
    (extend-protocol tr/ServerEvent
      KuunteleIlmoituksia
      (process-event [{opts :opts} {::tr/keys [e! client-id] :keys [kayttaja]} app]
        (kuuntele-ilmoituksia db e! kayttaja client-id opts)
        app)

      LopetaIlmoitustenKuuntelu
      (process-event [_ app]
        ;; TODO: Lopeta kuuntelijat tässä
        (lopeta-ilmoitusten-kuuntelu)
        app)))
  (stop [this]
    ;; TODO: Lopeta kuuntelijat myös tässä
    (lopeta-ilmoitusten-kuuntelu)))

(defn luo-ilmoitukset-ws []
  (->IlmoituksetWS))

