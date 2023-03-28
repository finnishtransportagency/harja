(ns harja.palvelin.palvelut.tuck-remoting.ilmoitukset
  "Ilmoitusten Tuck-remoting palvelu. Tämä käsittelee ilmoitusten WS server-eventit, joita lähetetään
  client-puolelta WebSocketin yli.
  Palvelu lähettää myös WS client-eventtejä palvelimelta clientille WebSocketin yli."
  (:require
    [harja.geo :as geo]
    [harja.kyselyt.konversio :as konversio]
    [taoensso.timbre :as log]
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as tr-komponentti]
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
#_(defn muodosta-ilmoitus-vastaus
  "Harja.transit read/write optiot eivät ole käytössä tuck-remoten omassa transit-toteutuksessa, joten muunnetaan
  ilmoitus-vastaus sellaiseksi, että se menee tuck-remoten läpi clientille."
  [ilmoitus]
  (-> ilmoitus
    (update-in [:sijainti] #(when % (geo/pg->clj %)))
    (update-in [:selitteet] #(when % (konversio/pgarray->vector %)))))

#_(defn hae-ilmoitus [db ilmoitus-id]
  (let [ilmoitus (first (tieliikenneilmoitus-q/hae-ilmoitukset-ilmoitusidlla db [ilmoitus-id]))]
    (muodosta-ilmoitus-vastaus ilmoitus)))


(defn kuuntele-ilmoituksia [db e! kayttaja client-id opts]
  (let [lopeta-kuuntelu-fn (if (:urakka-id opts)
                             (notifikaatiot/kuuntele-urakan-ilmoituksia (:urakka-id opts)
                               (fn [{ilmoitus-id :payload}]
                                 ;; Lähetetään asiakkaalle pelkkä uuden ilmoituksen ID.
                                 ;; Tämä toimii push-notifikaationa asiakkaalle, joka tekee päätöksen datan hakemisen
                                 ;; käynnistämisestä monimutkaisemman HTTP-rajapinnan kautta.
                                 (laheta-ilmoitus! e! {:ilmoitus-id ilmoitus-id})

                                 #_(let [ilmoitus (hae-ilmoitus db ilmoitus-id)]
                                   ;; TODO: Poista debug-lokitus
                                   (println "### Kuuntele urakan ilmoituksia, saatiin ilmoitus:" (pr-str ilmoitus))

                                   (laheta-ilmoitus! e! ilmoitus))))

                             (notifikaatiot/kuuntele-kaikkia-ilmoituksia
                               ;; TODO: Filtteröi ilmoituksen lähetys käyttäjän ja optioiden perusteella (Jeren kommentti)
                               (fn [{ilmoitus-id :payload}]
                                 ;; Lähetetään asiakkaalle pelkkä uuden ilmoituksen ID.
                                 ;; Tämä toimii push-notifikaationa asiakkaalle, joka tekee päätöksen datan hakemisen
                                 ;; käynnistämisestä monimutkaisemman HTTP-rajapinnan kautta.
                                 (laheta-ilmoitus! e! {:ilmoitus-id ilmoitus-id})

                                 #_(let [ilmoitus (hae-ilmoitus db ilmoitus-id)]
                                   ;; TODO: Poista debug-lokitus
                                   (println "### Kuuntele kaikkia ilmoituksia, saatiin ilmoitus:" (pr-str ilmoitus))

                                   (laheta-ilmoitus! e! ilmoitus)))))]
    (swap! kuuntelijat assoc client-id {:kayttaja kayttaja
                                        :e! e!
                                        :opts opts
                                        :lopeta-fn lopeta-kuuntelu-fn})))

(defn lopeta-ilmoitusten-kuuntelu [client-id]
  (let [kuuntelija (get-in @kuuntelijat [client-id])
        lopeta-fn (:lopeta-fn kuuntelija)]

    (when (fn? lopeta-fn)
      (log/info "Lopetetaan ilmoitusten kuuntelu ws-asiakkaalle: " client-id)
      (lopeta-fn))

    (swap! kuuntelijat dissoc client-id)))

(defrecord IlmoituksetWS []
  component/Lifecycle
  (start [{tuck-remoting :tuck-remoting db :db :as this}]
    ;; Varmista, että lopetataan ilmoitusten kuuntelu yksittäiselle asiakkaalle, kun asiakkaan WS-yhteys katkeaa.
    ;; Asiakkaan täytyy muodostaa yhteys uudelleen ja pyytää sen jälkeen kuuntelun aloittamista uudestaan
    (assoc this ::poista-yhteys-poikki-hook
                (tr-komponentti/rekisteroi-yhteys-poikki-hook!
                  tuck-remoting
                  (fn [{::tr/keys [e! client-id] :as client}]
                    (lopeta-ilmoitusten-kuuntelu client-id))))

    (extend-protocol tr/ServerEvent
      KuunteleIlmoituksia
      (process-event [{opts :opts} {::tr/keys [e! client-id] :keys [kayttaja]} app]
        (kuuntele-ilmoituksia db e! kayttaja client-id opts)
        app)

      LopetaIlmoitustenKuuntelu
      (process-event [_ {::tr/keys [client-id]} app]
        (lopeta-ilmoitusten-kuuntelu client-id)
        app)))

  (stop [{poista-yhteys-poikki-hook ::poista-yhteys-poikki-hook :as this}]
    ;; Poista Tuck-remoting yhteys-poikki hookin rekisteröinti
    (poista-yhteys-poikki-hook)

    ;; Poista kaikkien clienttien kuuntelijat
    (doseq [client-id (keys @kuuntelijat)]
      (lopeta-ilmoitusten-kuuntelu client-id))))

(defn luo-ilmoitukset-ws []
  (->IlmoituksetWS))

