(ns harja.palvelin.palvelut.tuck-remoting.ilmoitukset
  "Ilmoitusten Tuck-remoting palvelu. Tämä käsittelee ilmoitusten WS server-eventit, joita lähetetään
  client-puolelta WebSocketin yli.
  Palvelu lähettää myös WS client-eventtejä palvelimelta clientille WebSocketin yli."
  (:require
    [harja.domain.oikeudet :as oikeudet]
    [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
    [taoensso.timbre :as log]
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as tr-komponentti]
    [harja.tuck-remoting.ilmoitukset-eventit :as eventit]
    [com.stuartsierra.component :as component]
    [harja.kyselyt.tieliikenneilmoitukset :as tieliikenneilmoitus-q]
    [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot])
  ;; Käytetyt server-side eventit pitää lisäksi importata, jotta compile menee läpi
  (:import (harja.tuck_remoting.ilmoitukset_eventit KuunteleIlmoituksia LopetaIlmoitustenKuuntelu)))

(defonce kuuntelijat (atom {}))

(defn laheta-ilmoitus! [e! opts]
  (log/info "Lähetetään uusi ilmoitus (WS): " opts)

  (e! (eventit/->Ilmoitus opts)))

(defn hae-ilmoitus [db ilmoitus-id]
  (let [ilmoitus (tieliikenneilmoitus-q/hae-ilmoitus-ilmoitus-idlla db ilmoitus-id)]
    (first ilmoitus)))

(defn lopeta-ilmoitusten-kuuntelu [client-id]
  (let [kuuntelija (get-in @kuuntelijat [client-id])
        lopeta-fn (:lopeta-fn kuuntelija)]

    (when (fn? lopeta-fn)
      (log/info "Lopetetaan ilmoitusten kuuntelu ws-asiakkaalle: " client-id)
      (lopeta-fn))

    (swap! kuuntelijat dissoc client-id)))

(defn kuuntele-ilmoituksia [db e! kayttaja client-id suodattimet]
  ;; Varmista, että client-id:llä ei ole aktiivista kuuntelijaa, ennen kuin uusi kuuntelija käynnistetään
  (lopeta-ilmoitusten-kuuntelu client-id)

  (let [lopeta-kuuntelu-fn (if (:urakka suodattimet)
                             (let [;; Käytetään harja.palvelin.palvelut.ilmoitukset/hae-ilmoitukset käyttämää apufunktiota
                                   kayttajan-urakat (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
                                                      db kayttaja (fn [urakka-id kayttaja]
                                                                    (oikeudet/voi-lukea? oikeudet/ilmoitukset-ilmoitukset
                                                                      urakka-id
                                                                      kayttaja))
                                                      (:urakka suodattimet) nil nil nil nil nil)]
                               (notifikaatiot/kuuntele-urakan-ilmoituksia (:urakka suodattimet)
                                 (fn [{ilmoitus-id :payload}]
                                   (let [ilmoitus (hae-ilmoitus db ilmoitus-id)
                                         ilmoituksen-urakka (:urakka ilmoitus)
                                         laheta? (some (set kayttajan-urakat) #{ilmoituksen-urakka})]

                                     ;; Lähetetään asiakkaalle pelkkä uuden ilmoituksen ID.
                                     ;; Tämä toimii push-notifikaationa asiakkaalle, joka tekee päätöksen datan hakemisen
                                     ;; käynnistämisestä monimutkaisemman HTTP-rajapinnan kautta.
                                     (when laheta?
                                       (laheta-ilmoitus! e! {:ilmoitus-id ilmoitus-id}))))))

                             (let [;; Käytetään harja.palvelin.palvelut.ilmoitukset/hae-ilmoitukset käyttämää apufunktiota
                                   kayttajan-urakat (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
                                                      db kayttaja (fn [urakka-id kayttaja]
                                                                    (oikeudet/voi-lukea? oikeudet/ilmoitukset-ilmoitukset
                                                                      urakka-id
                                                                      kayttaja))
                                                      nil (:urakoitsija suodattimet)
                                                      (case (:urakkatyyppi suodattimet)
                                                        :kaikki nil
                                                        ; Ao. vesivayla-käsittely estää kantapoikkeuksen. Jos väylävalitsin tulee, tämä voidaan ehkä poistaa
                                                        :vesivayla :vesivayla-hoito
                                                        (:urakkatyyppi suodattimet))
                                                      (:hallintayksikko suodattimet)
                                                      nil nil)]
                               (notifikaatiot/kuuntele-kaikkia-ilmoituksia
                                 (fn [{ilmoitus-id :payload}]
                                   (let [ilmoitus (hae-ilmoitus db ilmoitus-id)
                                         ilmoituksen-urakka (:urakka ilmoitus)
                                         laheta? (some (set kayttajan-urakat) #{ilmoituksen-urakka})]

                                     ;; Lähetetään asiakkaalle pelkkä uuden ilmoituksen ID.
                                     ;; Tämä toimii push-notifikaationa asiakkaalle, joka tekee päätöksen datan hakemisen
                                     ;; käynnistämisestä monimutkaisemman HTTP-rajapinnan kautta.
                                     (when laheta?
                                       (laheta-ilmoitus! e! {:ilmoitus-id ilmoitus-id})))))))]
    (swap! kuuntelijat assoc client-id {:kayttaja kayttaja
                                        :e! e!
                                        :suodattimet suodattimet
                                        :lopeta-fn lopeta-kuuntelu-fn}))

  (log/info (str "Aloitettu ilmoitusten kuuntelu ws-asiakkaalle: " client-id ", suodattimet: " suodattimet ".")
    "Kuuntelijoiden lkm yhteensä: " (count @kuuntelijat)))

(defrecord IlmoituksetWS []
  component/Lifecycle
  (start [{tuck-remoting :tuck-remoting db :db :as this}]
    (extend-protocol tr/ServerEvent
      KuunteleIlmoituksia
      (process-event [{suodattimet :suodattimet} {::tr/keys [e! client-id] :keys [kayttaja]} app]
        (try
          (kuuntele-ilmoituksia db e! kayttaja client-id suodattimet)
          (e! (eventit/->IlmoitustenKuunteluOnnistui))
          (catch Exception e
            (log/error e (str "Ilmoitusten kuuntelijan käynnistäminen epäonnistui asiakkaalle: " client-id, ", kayttajanimi: " (:kayttajanimi kayttaja)))
            (e! (eventit/->IlmoitustenKuunteluEpaonnistui))))
        app)

      LopetaIlmoitustenKuuntelu
      (process-event [_ {::tr/keys [client-id]} app]
        (lopeta-ilmoitusten-kuuntelu client-id)
        app))

    ;; Varmista, että lopetataan ilmoitusten kuuntelu yksittäiselle asiakkaalle, kun asiakkaan WS-yhteys katkeaa.
    ;; Asiakkaan täytyy muodostaa yhteys uudelleen ja pyytää sen jälkeen kuuntelun aloittamista uudestaan
    (assoc this ::poista-yhteys-poikki-hook
                (tr-komponentti/rekisteroi-yhteys-poikki-hook!
                  tuck-remoting
                  (fn [{::tr/keys [e! client-id] :as client}]
                    (lopeta-ilmoitusten-kuuntelu client-id)))))

  (stop [{poista-yhteys-poikki-hook ::poista-yhteys-poikki-hook :as this}]
    ;; Poista Tuck-remoting yhteys-poikki hookin rekisteröinti
    (poista-yhteys-poikki-hook)

    ;; Poista kaikkien clienttien kuuntelijat
    (doseq [client-id (keys @kuuntelijat)]
      (lopeta-ilmoitusten-kuuntelu client-id))

    this))

(defn luo-ilmoitukset-ws []
  (->IlmoituksetWS))
