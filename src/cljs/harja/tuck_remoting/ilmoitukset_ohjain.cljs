(ns harja.tuck-remoting.ilmoitukset-ohjain
  (:require
    [taoensso.timbre :as log]
    [tuck.core :as tuck]
    [harja.tyokalut.tuck-remoting :as tr-tyokalut]
    [harja.tuck-remoting.ilmoitukset-eventit :as eventit]))

(defonce tila-atom (atom {:ilmoitukset []}))

(defrecord AloitaKuuntelu [])
(defrecord LopetaKuuntelu [])

(defn ws-yhteys-onnistui-kasittelija [e!]
  (log/info "Ilmoitukset: WS-yhteys aloitettu. Seurataan uusia ilmoituksia WS:n kautta.")
  ;; TODO: Kuunnellaan kovakoodatusti Oulun MHU urakkaa (35), ota käyttöliittmältä parametrina
  (e! (eventit/->KuunteleIlmoituksia {:urakka-id 35})))

(defn ws-yhteys-katkaistu-kasittelija [koodi syy suljettu-puhtaasti?]
  (if suljettu-puhtaasti?
    (log/info "Ilmoitukset: WS-yhteys katkaistu. Uusien ilmoitusten seuraaminen lopetettu.")
    (log/info "Ilmoitukset: WS-yhteys katkesi. Yritetään muodostaa yhteys uudelleen.")))

(extend-protocol tuck/Event
  eventit/Ilmoitus
  (process-event [{opts :opts} app]
    ;; TODO: Poista debug-lokitus
    (println "### saatiin ilmoitus!!!" opts)
    (update app :ilmoitukset conj opts))

  AloitaKuuntelu
  (process-event [_ app]
    (tuck/action!
      (fn [e!]
        (e! (tr-tyokalut/->YhdistaWS
              tila-atom
              (partial ws-yhteys-onnistui-kasittelija e!)
              ws-yhteys-katkaistu-kasittelija))))

    app)

  LopetaKuuntelu
  (process-event [_ app]
    (tuck/action!
      (fn [e!]
        (e! (tr-tyokalut/->KatkaiseWS))))
    app))
