(ns tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]
            [harja.pvm :as pvm]))

(declare kaynnista-sonja-uusiksi!)

(defn- varmista-sonjan-toimivuus! [this timeout]
  (let [sonja-yhteys-aloitettu? (::sonja-yhteys-aloitettu? this)
        uudelleen-kaynnistys-aika @(::uudelleen-kaynnistys-aika this)]
    {::sonja-yhteys-aloitettu (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-yhteys-aloitettu
                                                                     {:tyyppi {:palvelimet-viimeisin #{tapahtuma-apurit/host-nimi}}
                                                                      :timeout (* 1000 60 10)}
                                                                     (fn [{:keys [palvelin aika]} timeout?]
                                                                       (println "---- :sonja-yhteys-aloitettu ----")
                                                                       (println (str "---> timeout? " timeout?))
                                                                       (println (str "---> tämä palvelin " tapahtuma-apurit/host-nimi))
                                                                       (println (str "---> palvelin " palvelin))
                                                                       (println (str "---> @sonja-yhteys-aloitettu? " @sonja-yhteys-aloitettu?))
                                                                       (cond
                                                                         (and timeout?
                                                                              (not @sonja-yhteys-aloitettu?))
                                                                         (kaynnista-sonja-uusiksi! this)
                                                                         (or (nil? uudelleen-kaynnistys-aika)
                                                                             (pvm/jalkeen? uudelleen-kaynnistys-aika aika)) (reset! sonja-yhteys-aloitettu? true))))
     ::sonja-tila (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-tila
                                                         {:tyyppi {:palvelimet-viimeisin #{tapahtuma-apurit/host-nimi}}
                                                          :timeout timeout}
                                                         (fn [{:keys [palvelin payload]} timeout?]
                                                           (println "----- :sonja-tila -----")
                                                           (println (str "---> timeout? " timeout?))
                                                           (println (str "---> tämä palvelin " tapahtuma-apurit/host-nimi))
                                                           (println (str "--->  (= palvelin tapahtuma-apurit/host-nimi) " (= palvelin tapahtuma-apurit/host-nimi)))
                                                           (println (str "---> @sonja-yhteys-aloitettu? " @sonja-yhteys-aloitettu?))
                                                           (println (str "---> (not (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload))) " (not (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))
                                                           (when (or timeout?
                                                                     (and @sonja-yhteys-aloitettu?
                                                                          (not (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))
                                                             (kaynnista-sonja-uusiksi! this))))}))

(defn- sonjatarkkailu! [uudelleen-kaynnistaja]
  (let [varoaika (* 5 1000)
        {:keys [timeout-asetukset tapahtumien-tarkkailijat]} uudelleen-kaynnistaja]
    (swap! tapahtumien-tarkkailijat
           (fn [tarkkailijat]
             (merge tarkkailijat
                    (varmista-sonjan-toimivuus! uudelleen-kaynnistaja (+ (get-in timeout-asetukset [:sonja :paivitystiheys-ms]) varoaika)))))))

(defn- sonjan-uudelleen-kaynnistys-onnistui! [uudelleen-kaynnistaja & _]
  (println "SONJAN UUDELLEEN KÄYNNISTYS ONNISTUI!")
  (reset! (::sonja-yhteys-aloitettu? uudelleen-kaynnistaja) nil)
  (reset! (::uudelleen-kaynnistyksia uudelleen-kaynnistaja) 0)
  (sonjatarkkailu! uudelleen-kaynnistaja))

(defn- sonjan-uudelleen-kaynnistys-epaonnistui! [uudelleen-kaynnistaja & _]
  (println "SONJAN UUDELLEEN KÄYNNISTYS EPÄONNISTUI")
  (let [uudelleen-kaynnistyksia (::uudelleen-kaynnistyksia uudelleen-kaynnistaja)]
    (reset! uudelleen-kaynnistyksia 0)
    (tapahtuma-apurit/julkaise-tapahtuma :sonjan-uudelleenkaynnistys-epaonnistui tapahtuma-tulkkaus/tyhja-arvo)))

(defn- kaynnista-sonja-uusiksi! [uudelleen-kaynnistaja]
  (println "---> KÄYNNISTETÄÄN SONJA UUSIKSI!")
  (let [uudelleen-kaynnistyksia (::uudelleen-kaynnistyksia uudelleen-kaynnistaja)]
    (if (> @uudelleen-kaynnistyksia 0)
      (sonjan-uudelleen-kaynnistys-epaonnistui! uudelleen-kaynnistaja)
      (let [_ (swap! uudelleen-kaynnistyksia inc)
            _ (println "===========> 1")
            tapahtuman-odottaja (async/<!! (tapahtuma-apurit/kuuntele-tapahtumia :harjajarjestelman-restart-onnistui {:f (partial sonjan-uudelleen-kaynnistys-onnistui! uudelleen-kaynnistaja)
                                                                                                                      :tyyppi {:palvelimet-perus #{tapahtuma-apurit/host-nimi}}}
                                                                                 :harjajarjestelman-restart-epaonnistui {:f (partial sonjan-uudelleen-kaynnistys-epaonnistui! uudelleen-kaynnistaja)
                                                                                                                         :tyyppi {:palvelimet-perus #{tapahtuma-apurit/host-nimi}}}))]
        (println "===========> 2")
        (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (-> uudelleen-kaynnistaja (get :tapahtumien-tarkkailijat) deref ::sonja-yhteys-aloitettu))
        (println "===========> 3")
        (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (-> uudelleen-kaynnistaja (get :tapahtumien-tarkkailijat) deref ::sonja-tila))
        (println "===========> 4")
        (reset! (::uudelleen-kaynnistys-aika uudelleen-kaynnistaja) (pvm/nyt-suomessa))
        (println "===========> 5")
        (tapahtuma-apurit/julkaise-tapahtuma :harjajarjestelman-restart #{:sonja})
        (println "===========> 6")
        (async/<!! tapahtuman-odottaja)
        (println "===========> 7")))))

(defrecord UudelleenKaynnistaja [timeout-asetukset tapahtumien-tarkkailijat]
  component/Lifecycle
  (start [this]
    (let [this (assoc this ::sonja-yhteys-aloitettu? (atom nil)
                           ::uudelleen-kaynnistyksia (atom 0)
                           ::uudelleen-kaynnistys-aika (atom nil))]
      (sonjatarkkailu! this)
      this))
  (stop [this]
    (doseq [[_ tarkkailija] @(:tapahtumien-tarkkailijat this)]
      (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @tarkkailija))
    (reset! (::sonja-yhteys-aloitettu? this) nil)
    (reset! (::uudelleen-kaynnistyksia this) 0)
    (reset! (::uudelleen-kaynnistys-aika this) nil)
    (reset! tapahtumien-tarkkailijat nil)
    (dissoc this ::sonja-yhteys-aloitettu ::uudelleen-kaynnistyksia ::uudelleen-kaynnistys-aika)))
