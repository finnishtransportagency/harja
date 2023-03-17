(ns harja.tuck-remoting.ilmoitukset-palvelu
  (:require [harja.palvelin.komponentit.tuck-remoting :as tuck-remoting]
            [tuck.remoting :as tr]
            [harja.tuck-remoting.ilmoitukset-eventit :as eventit]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tieliikenneilmoitukset :as tieliikenneilmoitus-q]
            [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot])
  (:import (harja.tuck_remoting.ilmoitukset_eventit KuunteleIlmoituksia)))

(defn laheta-ilmoitus! [e! opts]
  (e! (eventit/->Ilmoitus opts)))

(defonce kuuntelijat (atom {}))

(defn hae-ilmoitus [db ilmoitus-id]
  (let [ilmoitus (tieliikenneilmoitus-q/hae-ilmoitukset-ilmoitusidlla db [ilmoitus-id])])
  )

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
                               (laheta-ilmoitus! e! {:ilmoitus (tieliikenneilmoitus-q/hae-ilmoitukset-ilmoitusidlla db [ilmoitus-id])}))))]
          (swap! kuuntelijat assoc client-id {:kayttaja kayttaja
                                              :e! e!
                                              :opts opts
                                              :lopeta-fn kuuntelija})))))
  (stop [this]
    ;; Lopeta kuuntelijat tässä
    )

  )

(defn tee-ilmoitusws []
  (->IlmoituksetWS))

