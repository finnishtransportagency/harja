(ns harja.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]))

(defn kaynnista-sonja-uusiksi! []
  )

(defn varmista-sonjan-toimivuus! [sonja timeout]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-tila
                                         {:tyyppi :viimeisin-per-palvelin
                                          :timeout timeout}
                                         (fn [{:keys [palvelin payload]} timeout?]
                                           (when (or timeout?
                                                     (and (= palvelin tapahtuma-apurit/host-nimi)
                                                          @(:yhteys-aloitettu? sonja)
                                                          (not (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))
                                             (kaynnista-sonja-uusiksi!)))))

(defrecord UudelleenKaynnistaja [timeout-asetukset]
  component/Lifecycle
  (start [this]
    (let [varoaika (* 5 1000)]
      (assoc this :sonja (varmista-sonjan-toimivuus! (:sonja this) (+ (get-in timeout-asetukset [:sonja :paivitystiheys-ms]) varoaika)))))
  (stop [this]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(:sonja this))
    this))
