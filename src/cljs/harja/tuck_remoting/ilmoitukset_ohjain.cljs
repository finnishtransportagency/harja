(ns harja.tuck-remoting.ilmoitukset-ohjain
  (:require
    [taoensso.timbre :as log]
    [tuck.core :as tuck]
    [harja.tyokalut.tuck-remoting :as tr-tyokalut]
    [harja.tuck-remoting.ilmoitukset-eventit :as eventit]
    [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as tieliikenneilmoitukset]
    [harja.tiedot.ilmoitukset.viestit :as v]))

(defrecord AloitaYhteysJaKuuntelu [])
(defrecord AloitaKuuntelu [opts])
(defrecord LopetaKuuntelu [])
(defrecord KatkaiseYhteys [])
(defrecord AsetaYhteydenTila [tila])

(defn ws-yhteys-onnistui-kasittelija [e!]
  (log/info "Ilmoitukset: WS-yhteys aloitettu. Seurataan uusia ilmoituksia WS:n kautta.")
  (e! (->AsetaYhteydenTila :aktiivinen))
  ;; TODO: Kuunnellaan kovakoodatusti Oulun MHU urakkaa (35), ota käyttöliittmältä parametrina
  (e! (->AloitaKuuntelu {:urakka-id 35})))

(defn ws-yhteys-katkaistu-kasittelija [e! koodi syy suljettu-puhtaasti?]
  (e! (->AsetaYhteydenTila :suljettu))

  ;; Laukaise ilmoitushaku ja automaattinen ilmoitusten HTTP-pollaus, jos WS-yhteys katkeaa
  (e! (v/->HaeIlmoitukset))

  (if suljettu-puhtaasti?
    (log/info "Ilmoitukset: WS-yhteys katkaistu. Uusien ilmoitusten seuraaminen lopetettu.")
    (log/info "Ilmoitukset: WS-yhteys katkesi. Yritetään muodostaa yhteys uudelleen.")))

(extend-protocol tuck/Event
  ;; Aloittaa WS-yhteyden ja lähettää kuuntelun aloittamisen käynnistävän viestin palvelimelle
  ;; ws-yhteys-onnistui-kasittelija -käsittelijässä.
  AloitaYhteysJaKuuntelu
  (process-event [_ app]
    (tuck/action!
      (fn [e!]
        (e! (tr-tyokalut/->YhdistaWS
              ;; TODO: Testataan suoraan tilan muuttamistan tieliikenneilmoitukset-atomiin
              tieliikenneilmoitukset/ilmoitukset
              (partial ws-yhteys-onnistui-kasittelija e!)
              (partial ws-yhteys-katkaistu-kasittelija e!)))))

    app)

  AsetaYhteydenTila
  (process-event [{tila :tila} app]
    ;; Huom. tämä asetetaan suoraan tuck-remotingille annettuun tila-atomiin
    (assoc-in app [:ws-yhteyden-tila] tila))

  AloitaKuuntelu
  (process-event [{opts :opts} app]
    (tuck/action!
      (fn [e!]
        (e! (eventit/->KuunteleIlmoituksia opts))))

    app)

  LopetaKuuntelu
  (process-event [_ app]
    (tuck/action!
      (fn [e!]
        (e! (eventit/->LopetaIlmoitustenKuuntelu))))
    app)

  ;; Jos haluat katkaista yhteyden ja lopettaa kuuntelijat palvelimella, pelkkä yhteyden katkaisu riittää.
  ;; NS harja.palvelin.palvelut.tuck-remoting.ilmoitukset huolehtii siitä,
  ;; että kuuntelijat kytketään pois päältä, kun yhteys katkaistaan.
  KatkaiseYhteys
  (process-event [_ app]
    (tuck/action!
      (fn [e!]
        (e! (tr-tyokalut/->KatkaiseWS))))
    app)

  ;; -- Tuck-remoting eventtien käsittely --
  eventit/Ilmoitus
  (process-event [{:keys [ilmoitus]} app]
    (log/info "Uusi ilmoitus saatavilla (WS): " (:ilmoitus-id ilmoitus) ". Laukaistaan ilmoitusten haku.")

    ;; Laukaise yksittäinen ilmoitushaku, kun saadaan ilmoitus uudesta ilmoituksesta
    (tuck/action!
      (fn [e!]
        (e! (v/->HaeIlmoitukset))))

    ;; Laita talteen uusimman ilmoituksen ID
    (assoc-in app [:ws-uusin-ilmoitus] (:ilmoitus-id ilmoitus))))
