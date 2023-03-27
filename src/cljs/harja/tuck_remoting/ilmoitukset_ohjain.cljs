(ns harja.tuck-remoting.ilmoitukset-ohjain
  (:require
    [taoensso.timbre :as log]
    [tuck.core :as tuck]
    [harja.tyokalut.tuck-remoting :as tr-tyokalut]
    [harja.tuck-remoting.ilmoitukset-eventit :as eventit]
    [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as tieliikenneilmoitukset]))

(defonce tila-atom (atom {:ws-ilmoitukset []
                          :ws-yhteyden-tila :suljettu}))

(defrecord AloitaKuuntelu [])
(defrecord LopetaKuuntelu [])
(defrecord AsetaYhteydenTila [tila])

(defn ws-yhteys-onnistui-kasittelija [e!]
  (log/info "Ilmoitukset: WS-yhteys aloitettu. Seurataan uusia ilmoituksia WS:n kautta.")
  ;; TODO: Kuunnellaan kovakoodatusti Oulun MHU urakkaa (35), ota käyttöliittmältä parametrina
  (e! (->AsetaYhteydenTila :aktiivinen))
  (e! (eventit/->KuunteleIlmoituksia {:urakka-id 35})))

(defn ws-yhteys-katkaistu-kasittelija [e! koodi syy suljettu-puhtaasti?]
  (e! (->AsetaYhteydenTila :suljettu))

  (if suljettu-puhtaasti?
    (log/info "Ilmoitukset: WS-yhteys katkaistu. Uusien ilmoitusten seuraaminen lopetettu.")
    (log/info "Ilmoitukset: WS-yhteys katkesi. Yritetään muodostaa yhteys uudelleen.")))

(extend-protocol tuck/Event
  AsetaYhteydenTila
  (process-event [{tila :tila} app]
    (assoc-in app [:ws-yhteyden-tila] tila))

  AloitaKuuntelu
  (process-event [_ app]
    (tuck/action!
      (fn [e!]
        (e! (tr-tyokalut/->YhdistaWS
              tila-atom
              ;; Testataan suoraan tilan muuttamistan tieliikenneilmoitukset-atomiin
              #_tieliikenneilmoitukset/ilmoitukset
              (partial ws-yhteys-onnistui-kasittelija e!)
              (partial ws-yhteys-katkaistu-kasittelija e!)))))

    app)

  LopetaKuuntelu
  (process-event [_ app]
    (tuck/action!
      (fn [e!]
        ;; Lopeta ilmoitusten kuuntelu ja katkaise WS-yhteys
        (e! (eventit/->LopetaIlmoitustenKuuntelu))
        (e! (tr-tyokalut/->KatkaiseWS))))
    app)

  eventit/Ilmoitus
  (process-event [{:keys [ilmoitus]} app]
    (log/info "Uusi ilmoitus (WS):" ilmoitus)
    (update app :ws-ilmoitukset conj ilmoitus)))
