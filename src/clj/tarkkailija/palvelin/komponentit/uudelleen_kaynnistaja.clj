(ns tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]))

(defonce uudelleen-kaynnistyksia (atom 0))

(defn- sonjan-uudelleen-kaynnistys-onnistui! []
  ;;TODO Aloita Sonjan kuuntelu uudestaan
  )

(defn- sonjan-uudelleen-kaynnistys-epaonnistui! []
  (reset! uudelleen-kaynnistyksia 0)
  ;; TODO status punaseksi
  )

(defn- kaynnista-sonja-uusiksi! [uudelleen-kaynnistaja]
  (if (> @uudelleen-kaynnistyksia 0)
    (sonjan-uudelleen-kaynnistys-epaonnistui!)
    (let [_ (swap! uudelleen-kaynnistyksia inc)
          tapahtuman-odottaja (async/<!! (tapahtuma-apurit/kuuntele-tapahtumia :harjajarjestelman-restart-onnistui sonjan-uudelleen-kaynnistys-onnistui!
                                                                               :harjajarjestelman-restart-epaonnistui sonjan-uudelleen-kaynnistys-epaonnistui!))]
      (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (get-in uudelleen-kaynnistyksia [:tapahtumien-kuuntelijat ::sonja-yhteys-aloitettu]))
      (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (get-in uudelleen-kaynnistyksia [:tapahtumien-kuuntelijat ::sonja-tila]))
      (tapahtuma-apurit/julkaise-tapahtuma :harjajarjestelman-restart #{:sonja})
      (async/<!! tapahtuman-odottaja))))

(defn varmista-sonjan-toimivuus! [this timeout]
  (let [sonja-yhteys-aloitettu (atom nil)]
    {::sonja-yhteys-aloitettu (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-yhteys-aloitettu
                                                                     {:tyyppi :viimeisin-per-palvelin
                                                                      :timeout (* 1000 60 10)}
                                                                     (fn [{:keys [palvelin]} timeout?]
                                                                       ;; uudelleen käynnistystä
                                                                       (when (and timeout?
                                                                                  (not (contains? @sonja-yhteys-aloitettu tapahtuma-apurit/host-nimi)))
                                                                         (kaynnista-sonja-uusiksi! this))
                                                                       (swap! sonja-yhteys-aloitettu assoc palvelin true)))
     ::sonja-tila (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-tila
                                                         {:tyyppi :viimeisin-per-palvelin
                                                          :timeout timeout}
                                                         (fn [{:keys [palvelin payload]} timeout?]
                                                           (when (or timeout?
                                                                     (and (= palvelin tapahtuma-apurit/host-nimi)
                                                                          (contains? @sonja-yhteys-aloitettu tapahtuma-apurit/host-nimi)
                                                                          (not (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))
                                                             (kaynnista-sonja-uusiksi! this))))}))

(defrecord UudelleenKaynnistaja [timeout-asetukset tapahtumien-tarkkailijat]
  component/Lifecycle
  (start [this]
    (let [varoaika (* 5 1000)]
      (reset! tapahtumien-tarkkailijat (varmista-sonjan-toimivuus! this (+ (get-in timeout-asetukset [:sonja :paivitystiheys-ms]) varoaika)))
      this))
  (stop [this]
    (doseq [[_ tarkkailija] @(:tapahtumien-tarkkailijat this)]
      (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @tarkkailija))
    (reset! tapahtumien-tarkkailijat nil)
    this))
