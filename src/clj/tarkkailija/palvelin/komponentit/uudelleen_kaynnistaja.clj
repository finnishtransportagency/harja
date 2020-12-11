(ns tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]
            [harja.palvelin.asetukset :as asetukset]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]
            [harja.pvm :as pvm]))

(declare kaynnista-sonja-uusiksi!)

(defn- varmista-sonjan-toimivuus! [this timeout]
  (let [sonja-yhteys-aloitettu? (::sonja-yhteys-aloitettu? this)
        uudelleen-kaynnistys-aika (::uudelleen-kaynnistys-aika this)
        viestien-maara-kaynnistyksesta (::viestien-maara-kaynnistyksesta this)]
    {::sonja-yhteys-aloitettu (tapahtuma-apurit/tarkkaile-tapahtumaa
                                :sonja-yhteys-aloitettu
                                {:tyyppi {:palvelimet-viimeisin #{tapahtuma-apurit/host-nimi}}
                                 :timeout (* 1000 60 10)}
                                (fn [{:keys [palvelin aika]} timeout?]
                                  (cond
                                    (and timeout?
                                         (not @sonja-yhteys-aloitettu?))
                                    (do (log/error ":sonja-yhteys-aloitettu päättyi timeoutiin eikä Sonjayhteyttä oltu aloitettu")
                                        (kaynnista-sonja-uusiksi! this))
                                    (or (nil? @uudelleen-kaynnistys-aika)
                                        ;; yhteys aloitettu uudelleen käynnistämisen jälkeen?
                                        (pvm/jalkeen? aika @uudelleen-kaynnistys-aika)) (reset! sonja-yhteys-aloitettu? true))))
     ::sonja-tila (tapahtuma-apurit/tarkkaile-tapahtumaa
                    :sonja-tila
                    {:tyyppi {:palvelimet-viimeisin #{tapahtuma-apurit/host-nimi}}
                     :timeout timeout}
                    (fn [{:keys [palvelin payload]} timeout?]
                      (let [sonjayhteys-ok? (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload))]
                        (swap! viestien-maara-kaynnistyksesta inc)
                        (when (or timeout?
                                  (and @sonja-yhteys-aloitettu?
                                       (not sonjayhteys-ok?)
                                       (> @viestien-maara-kaynnistyksesta 3)))
                          (kaynnista-sonja-uusiksi! this)))))}))

(defn- sonjatarkkailu! [uudelleen-kaynnistaja]
  (let [varoaika (* 5 1000)
        {:keys [timeout-asetukset tapahtumien-tarkkailijat]} uudelleen-kaynnistaja]
    (swap! tapahtumien-tarkkailijat
           (fn [tarkkailijat]
             (merge tarkkailijat
                    (varmista-sonjan-toimivuus! uudelleen-kaynnistaja (+ (get-in timeout-asetukset [:sonja :paivitystiheys-ms]) varoaika)))))))

(defn- sonjan-uudelleen-kaynnistys-onnistui! [uudelleen-kaynnistaja & _]
  (log/info "SONJAN UUDELLEEN KÄYNNISTYS ONNISTUI!")
  (reset! (::uudelleen-kaynnistyksia uudelleen-kaynnistaja) 0)
  (sonjatarkkailu! uudelleen-kaynnistaja))

(defn- sonjan-uudelleen-kaynnistys-epaonnistui! [uudelleen-kaynnistaja & _]
  (log/info "SONJAN UUDELLEEN KÄYNNISTYS EPÄONNISTUI")
  (let [uudelleen-kaynnistyksia (::uudelleen-kaynnistyksia uudelleen-kaynnistaja)]
    (reset! uudelleen-kaynnistyksia 0)
    (tapahtuma-apurit/julkaise-tapahtuma :sonjan-uudelleenkaynnistys-epaonnistui tapahtuma-tulkkaus/tyhja-arvo)))

(defn- kaynnista-sonja-uusiksi! [uudelleen-kaynnistaja]
  (log/info "---> KÄYNNISTETÄÄN SONJA UUSIKSI!")
  (let [uudelleen-kaynnistyksia (::uudelleen-kaynnistyksia uudelleen-kaynnistaja)]
    (if (> @uudelleen-kaynnistyksia 0)
      (sonjan-uudelleen-kaynnistys-epaonnistui! uudelleen-kaynnistaja)
      (let [_ (swap! uudelleen-kaynnistyksia inc)
            tapahtuman-odottaja (async/<!! (tapahtuma-apurit/kuuntele-tapahtumia :harjajarjestelman-restart-onnistui {:f (partial sonjan-uudelleen-kaynnistys-onnistui! uudelleen-kaynnistaja)
                                                                                                                      :tyyppi {:palvelimet-perus #{tapahtuma-apurit/host-nimi}}}
                                                                                 :harjajarjestelman-restart-epaonnistui {:f (partial sonjan-uudelleen-kaynnistys-epaonnistui! uudelleen-kaynnistaja)
                                                                                                                         :tyyppi {:palvelimet-perus #{tapahtuma-apurit/host-nimi}}}))]
        (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (-> uudelleen-kaynnistaja (get :tapahtumien-tarkkailijat) deref ::sonja-yhteys-aloitettu deref))
        (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (-> uudelleen-kaynnistaja (get :tapahtumien-tarkkailijat) deref ::sonja-tila deref))
        (reset! (::sonja-yhteys-aloitettu? uudelleen-kaynnistaja) nil)
        (reset! (::viestien-maara-kaynnistyksesta uudelleen-kaynnistaja) 0)
        (reset! (::uudelleen-kaynnistys-aika uudelleen-kaynnistaja) (pvm/nyt-suomessa))
        (tapahtuma-apurit/julkaise-tapahtuma :harjajarjestelman-restart #{:sonja})
        (async/<!! tapahtuman-odottaja)))))

(defonce start-lukko (Object.))

(defrecord UudelleenKaynnistaja [timeout-asetukset tapahtumien-tarkkailijat]
  component/Lifecycle
  (start [this]
    (locking start-lukko
      (when-not (contains? this ::sonja-yhteys-aloitettu)
        (let [this (assoc this ::sonja-yhteys-aloitettu? (atom nil)
                               ::uudelleen-kaynnistyksia (atom 0)
                               ::viestien-maara-kaynnistyksesta (atom 0)
                               ::uudelleen-kaynnistys-aika (atom nil))]
          (if (asetukset/ominaisuus-kaytossa? :sonja-uudelleen-kaynnistys)
            (sonjatarkkailu! this)
            (log/info "Sonja uudelleen käynnistys ei ole käytössä"))
          this))))
  (stop [this]
    (when (:tapahtumien-tarkkailijat this)
      (doseq [[_ tarkkailija] @(:tapahtumien-tarkkailijat this)]
        (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @tarkkailija)))
    (reset! (::sonja-yhteys-aloitettu? this) nil)
    (reset! (::uudelleen-kaynnistyksia this) 0)
    (reset! (::viestien-maara-kaynnistyksesta this) 0)
    (reset! (::uudelleen-kaynnistys-aika this) nil)
    (reset! tapahtumien-tarkkailijat nil)
    (dissoc this ::sonja-yhteys-aloitettu ::uudelleen-kaynnistyksia ::uudelleen-kaynnistys-aika)))
