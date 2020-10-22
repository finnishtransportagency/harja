(ns harja.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]))

;; TODO tämä komponentti ei saisi olla oikeastaan pää Harjasysteemissä, koska tämäkinhän käynnistetään mahdollisesti
;; uudestaaan.


(defn- sonjan-uudelleen-kaynnistys-onnistui! []
  ;;TODO Aloita Sonjan kuuntelu uudestaan
  )

(defn- sonjan-uudelleen-kaynnistys-epaonnistui! []
  ;; TODO status punaseksi
  )

(defn- kaynnista-sonja-uusiksi! [sonjan-kuuntelu]
  (let [tapahtuman-odottaja (async/<!! (tapahtuma-apurit/kuuntele-tapahtumia :harjajarjestelman-restart-onnistui sonjan-uudelleen-kaynnistys-onnistui!
                                                                             :harjajarjestelman-restart-epaonnistui sonjan-uudelleen-kaynnistys-epaonnistui!))]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @sonjan-kuuntelu)
    (tapahtuma-apurit/julkaise-tapahtuma :harjajarjestelman-restart #{:sonja})
    (async/<!! tapahtuman-odottaja)))

(defn varmista-sonjan-toimivuus! [this timeout]
  (let [sonja (get this :sonja)
        kaynnista-sonja-uusiksi! (partial kaynnista-sonja-uusiksi! (:sonjatilan-kuuntelija this))]
    (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-tila
                                           {:tyyppi :viimeisin-per-palvelin
                                            :timeout timeout}
                                           (fn [{:keys [palvelin payload]} timeout?]
                                             (when (or timeout?
                                                       (and (= palvelin tapahtuma-apurit/host-nimi)
                                                            @(:yhteys-aloitettu? sonja)
                                                            (not (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))
                                               (kaynnista-sonja-uusiksi!))))))

(defrecord UudelleenKaynnistaja [timeout-asetukset]
  component/Lifecycle
  (start [this]
    (let [varoaika (* 5 1000)]
      (assoc this :sonjatilan-kuuntelija (varmista-sonjan-toimivuus! this (+ (get-in timeout-asetukset [:sonja :paivitystiheys-ms]) varoaika)))))
  (stop [this]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(:sonjatilan-kuuntelija this))
    this))
