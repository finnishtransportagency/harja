(ns harja.palvelin.palvelut.tuck-remoting.ilmoitukset
  "Ilmoitusten Tuck-remoting palvelu. Tämä käsittelee ilmoitusten WS server-eventit, joita lähetetään
  client-puolelta WebSocketin yli.
  Palvelu lähettää myös WS client-eventtejä palvelimelta clientille WebSocketin yli."
  (:require
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as tuck-remoting]
    [harja.tuck-remoting.ilmoitukset-eventit :as eventit]
    [com.stuartsierra.component :as component]
    [harja.kyselyt.tieliikenneilmoitukset :as tieliikenneilmoitus-q]
    [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot])
  ;; Käytetyt server-side eventit pitää lisäksi importata, jotta compile menee läpi
  (:import (harja.tuck_remoting.ilmoitukset_eventit KuunteleIlmoituksia)))

(defn laheta-ilmoitus! [e! opts]
  (e! (eventit/->Ilmoitus opts)))

(defonce kuuntelijat (atom {}))

(defn hae-ilmoitus [db ilmoitus-id]
  ;; TODO
  (let [ilmoitus (tieliikenneilmoitus-q/hae-ilmoitukset-ilmoitusidlla db [ilmoitus-id])]))

(defrecord IlmoituksetWS []
  component/Lifecycle
  (start [{tuck-remoting :tuck-remoting db :db :as this}]
    (extend-protocol tr/ServerEvent
      KuunteleIlmoituksia
      (process-event [{opts :opts} {::tr/keys [e! client-id] :keys [kayttaja]} _]
        (let [kuuntelija (if (:urakka-id opts)
                           (notifikaatiot/kuuntele-urakan-ilmoituksia (:urakka-id opts)
                             (fn [{ilmoitus-id :payload}]
                               (let [ilmoitus (tieliikenneilmoitus-q/hae-ilmoitukset-ilmoitusidlla db [ilmoitus-id])]
                                 (laheta-ilmoitus! e! {:ilmoitus (:id ilmoitus)}))))
                           (notifikaatiot/kuuntele-kaikkia-ilmoituksia
                             ;; TODO: Filtteröi ilmoituksen lähetys käyttäjän ja optioiden perusteella
                             (fn [{ilmoitus-id :payload}]
                               (laheta-ilmoitus! e!
                                 {:ilmoitus
                                  (tieliikenneilmoitus-q/hae-ilmoitukset-ilmoitusidlla db [ilmoitus-id])}))))]
          (swap! kuuntelijat assoc client-id {:kayttaja kayttaja
                                              :e! e!
                                              :opts opts
                                              :lopeta-fn kuuntelija})))

      #_#_eventit/LopetaIlmoitustenKuuntelu
      (process-event []
        ;; TODO: Lopeta kuuntelijat tässä
        )))
  (stop [this]
    ;; TODO: Lopeta kuuntelijat myös tässä
    )

  )

(defn luo-ilmoitukset-ws []
  (->IlmoituksetWS))

