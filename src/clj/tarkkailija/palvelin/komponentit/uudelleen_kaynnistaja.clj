(ns tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]))

(defonce uudelleen-kaynnistyksia (atom 0))

(declare kaynnista-sonja-uusiksi!)

(defn- varmista-sonjan-toimivuus! [this timeout]
  (let [sonja-yhteys-aloitettu (atom nil)]
    {::sonja-yhteys-aloitettu (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-yhteys-aloitettu
                                                                     {:tyyppi :viimeisin-per-palvelin
                                                                      :timeout (* 1000 60 10)}
                                                                     (fn [{:keys [palvelin]} timeout?]
                                                                       (if (and timeout?
                                                                                (not (contains? @sonja-yhteys-aloitettu tapahtuma-apurit/host-nimi)))
                                                                         (kaynnista-sonja-uusiksi! this)
                                                                         (swap! sonja-yhteys-aloitettu assoc palvelin true))))
     ::sonja-tila (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-tila
                                                         {:tyyppi :viimeisin-per-palvelin
                                                          :timeout timeout}
                                                         (fn [{:keys [palvelin payload]} timeout?]
                                                           (when (or timeout?
                                                                     (and (= palvelin tapahtuma-apurit/host-nimi)
                                                                          (contains? @sonja-yhteys-aloitettu tapahtuma-apurit/host-nimi)
                                                                          (not (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))
                                                             (kaynnista-sonja-uusiksi! this))))}))

(defn- sonjatarkkailu! [uudelleen-kaynnistaja]
  (let [varoaika (* 5 1000)
        {:keys [timeout-asetukset tapahtumien-tarkkailijat]} uudelleen-kaynnistaja]
    (swap! tapahtumien-tarkkailijat
           (fn [tarkkailijat]
             (merge tarkkailijat
                    (varmista-sonjan-toimivuus! uudelleen-kaynnistaja (+ (get-in timeout-asetukset [:sonja :paivitystiheys-ms]) varoaika)))))))

(defn- sonjan-uudelleen-kaynnistys-onnistui! [uudelleen-kaynnistaja]
  (reset! uudelleen-kaynnistyksia 0)
  (sonjatarkkailu! uudelleen-kaynnistaja))

(defn- sonjan-uudelleen-kaynnistys-epaonnistui! []
  (reset! uudelleen-kaynnistyksia 0)
  (tapahtuma-apurit/julkaise-tapahtuma :sonjan-uudelleenkaynnistys-epaonnistui nil))

(defn- kaynnista-sonja-uusiksi! [uudelleen-kaynnistaja]
  (if (> @uudelleen-kaynnistyksia 0)
    (sonjan-uudelleen-kaynnistys-epaonnistui!)
    (let [_ (swap! uudelleen-kaynnistyksia inc)
          tapahtuman-odottaja (async/<!! (tapahtuma-apurit/kuuntele-tapahtumia :harjajarjestelman-restart-onnistui (partial sonjan-uudelleen-kaynnistys-onnistui! uudelleen-kaynnistaja)
                                                                               :harjajarjestelman-restart-epaonnistui sonjan-uudelleen-kaynnistys-epaonnistui!))]
      (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (-> uudelleen-kaynnistaja (get :tapahtumien-tarkkailijat) deref ::sonja-yhteys-aloitettu))
      (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (-> uudelleen-kaynnistaja (get :tapahtumien-tarkkailijat) deref ::sonja-tila))
      (tapahtuma-apurit/julkaise-tapahtuma :harjajarjestelman-restart #{:sonja})
      (async/<!! tapahtuman-odottaja))))

(defrecord UudelleenKaynnistaja [timeout-asetukset tapahtumien-tarkkailijat]
  component/Lifecycle
  (start [this]
    (sonjatarkkailu! this)
    this)
  (stop [this]
    (doseq [[_ tarkkailija] @(:tapahtumien-tarkkailijat this)]
      (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @tarkkailija))
    (reset! tapahtumien-tarkkailijat nil)
    this))
