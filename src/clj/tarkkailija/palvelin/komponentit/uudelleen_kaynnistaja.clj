(ns tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]
            [harja.palvelin.asetukset :as asetukset]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]
            [harja.pvm :as pvm]))

(declare kaynnista-jarjestelma-uusiksi!)

(def tama-namespace (namespace ::this))

(defn- varmista-jms-toimivuus! [this jarjestelma timeout]
  (let [jarjestelma-ns-kw (keyword tama-namespace jarjestelma)
        yhteys-aloitettu-ns-ks (keyword tama-namespace (str jarjestelma "-yhteys-aloitettu"))
        tila-ns-ks (keyword tama-namespace (str jarjestelma "-tila"))
        tila-atom (::tila this)
        yhteys-aloitettu? (fn [] (-> tila-atom deref jarjestelma-ns-kw ::yhteys-aloitettu?))
        merkkaa-yhteys-aloitetuksi! (fn [] (swap! tila-atom assoc-in [jarjestelma-ns-kw ::yhteys-aloitettu?] true))
        uudelleen-kaynnistys-aika (fn [] (-> tila-atom deref jarjestelma-ns-kw ::uudelleen-kaynnistys-aika))
        viestien-maara-kaynnistyksesta (fn [] (-> tila-atom deref jarjestelma-ns-kw ::viestien-maara-kaynnistyksesta))
        inc-viestien-maara-kaynnistuksesta (fn [] (swap! tila-atom update-in [jarjestelma-ns-kw ::viestien-maara-kaynnistyksesta] inc))]
    {yhteys-aloitettu-ns-ks (tapahtuma-apurit/tarkkaile-tapahtumaa
                              :jms-yhteys-aloitettu
                              {:tyyppi {:palvelimet-viimeisin #{tapahtuma-apurit/host-nimi}}
                               :timeout (* 1000 60 10)}
                              (fn [{:keys [palvelin payload aika]} timeout?]
                                (when (= (:jarjestelma payload) jarjestelma)
                                  (cond
                                    (and timeout?
                                         (not (yhteys-aloitettu?)))
                                    (do (log/error (str yhteys-aloitettu-ns-ks " päättyi timeoutiin eikä yhteyttä oltu aloitettu"))
                                        (kaynnista-jarjestelma-uusiksi! this jarjestelma))
                                    (or (nil? (uudelleen-kaynnistys-aika))
                                        ;; yhteys aloitettu uudelleen käynnistämisen jälkeen?
                                        (pvm/jalkeen? aika (uudelleen-kaynnistys-aika))) (merkkaa-yhteys-aloitetuksi!)))))
     tila-ns-ks (tapahtuma-apurit/tarkkaile-tapahtumaa
                  :jms-tila
                  {:tyyppi {:palvelimet-viimeisin #{tapahtuma-apurit/host-nimi}}
                   :timeout timeout}
                  (fn [{:keys [palvelin payload]} timeout?]
                    (when-let [jarjestelma-payload (get payload jarjestelma)]
                      (let [yhteys-ok? (tapahtuma-tulkkaus/jmsyhteys-ok? jarjestelma-payload)]
                        (inc-viestien-maara-kaynnistuksesta)
                        (when (or timeout?
                                  (and (yhteys-aloitettu?)
                                       (not yhteys-ok?)
                                       (> (viestien-maara-kaynnistyksesta) 3)))
                          (kaynnista-jarjestelma-uusiksi! this jarjestelma))))))}))

(defn- varmista-sonjan-toimivuus! [this timeout]
  (varmista-jms-toimivuus! this "sonja" timeout))

(defn- varmista-itmfn-toimivuus! [this timeout]
  (varmista-jms-toimivuus! this "itmf" timeout))

(defn- jmstarkkailu! [jarjestelma uudelleen-kaynnistaja]
  (let [varoaika (* 5 1000)
        {:keys [timeout-asetukset tapahtumien-tarkkailijat]} uudelleen-kaynnistaja]
    (swap! tapahtumien-tarkkailijat
           (fn [tarkkailijat]
             (merge tarkkailijat
                    (case jarjestelma
                      "sonja" (varmista-sonjan-toimivuus! uudelleen-kaynnistaja (+ (get-in timeout-asetukset [:sonja :paivitystiheys-ms]) varoaika))
                      "itmf" (varmista-itmfn-toimivuus! uudelleen-kaynnistaja (+ (get-in timeout-asetukset [:itmf :paivitystiheys-ms]) varoaika))))))))

(defn- jarjestelman-uudelleen-kaynnistys-onnistui! [uudelleen-kaynnistaja jarjestelma & _]
  (log/info (str jarjestelma " UUDELLEEN KÄYNNISTYS ONNISTUI!"))
  (let [jarjestelma-ns-kw (keyword tama-namespace jarjestelma)
        tila-atom (::tila uudelleen-kaynnistaja)]
    (swap! tila-atom assoc-in [jarjestelma-ns-kw ::uudelleen-kaynnistyksia] 0)
    (jmstarkkailu! jarjestelma uudelleen-kaynnistaja)))

(defn- jarjestelman-uudelleen-kaynnistys-epaonnistui! [uudelleen-kaynnistaja jarjestelma & _]
  (log/info (str jarjestelma " UUDELLEEN KÄYNNISTYS EPÄONNISTUI"))
  (let [jarjestelma-ns-kw (keyword tama-namespace jarjestelma)
        tila-atom (::tila uudelleen-kaynnistaja)]
    (swap! tila-atom assoc-in [jarjestelma-ns-kw ::uudelleen-kaynnistyksia] 0)
    (tapahtuma-apurit/julkaise-tapahtuma :jms-uudelleenkaynnistys-epaonnistui jarjestelma)))

(defn- kaynnista-jarjestelma-uusiksi! [uudelleen-kaynnistaja jarjestelma]
  (log/info "---> KÄYNNISTETÄÄN " jarjestelma " UUSIKSI!")
  (let [jarjestelma-ns-kw (keyword tama-namespace jarjestelma)
        yhteys-aloitettu-ns-ks (keyword tama-namespace (str jarjestelma "-yhteys-aloitettu"))
        tila-ns-ks (keyword tama-namespace (str jarjestelma "-tila"))
        tila-atom (::tila uudelleen-kaynnistaja)
        uudelleen-kaynnistysten-maara (fn [] (-> tila-atom deref jarjestelma-ns-kw ::uudelleen-kaynnistyksia))
        inc-uudelleen-kaynnistyksia (fn [] (swap! tila-atom update-in [jarjestelma-ns-kw ::uudelleen-kaynnistyksia] inc))]
    (if (> (uudelleen-kaynnistysten-maara) 0)
      (jarjestelman-uudelleen-kaynnistys-epaonnistui! uudelleen-kaynnistaja jarjestelma)
      (let [_ (inc-uudelleen-kaynnistyksia)
            tapahtuman-odottaja (async/<!! (tapahtuma-apurit/kuuntele-tapahtumia :harjajarjestelman-restart-onnistui {:f (partial jarjestelman-uudelleen-kaynnistys-onnistui! uudelleen-kaynnistaja jarjestelma)
                                                                                                                      :tyyppi {:palvelimet-perus #{tapahtuma-apurit/host-nimi}}}
                                                                                 :harjajarjestelman-restart-epaonnistui {:f (partial jarjestelman-uudelleen-kaynnistys-epaonnistui! uudelleen-kaynnistaja jarjestelma)
                                                                                                                         :tyyppi {:palvelimet-perus #{tapahtuma-apurit/host-nimi}}}))]
        (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (-> uudelleen-kaynnistaja (get :tapahtumien-tarkkailijat) deref yhteys-aloitettu-ns-ks deref))
        (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (-> uudelleen-kaynnistaja (get :tapahtumien-tarkkailijat) deref tila-ns-ks deref))
        (swap! tila-atom assoc-in [jarjestelma-ns-kw ::yhteys-aloitettu?] nil)
        (swap! tila-atom assoc-in [jarjestelma-ns-kw ::viestien-maara-kaynnistyksesta] 0)
        (swap! tila-atom assoc-in [jarjestelma-ns-kw ::uudelleen-kaynnistys-aika] (pvm/nyt-suomessa))
        (tapahtuma-apurit/julkaise-tapahtuma :harjajarjestelman-restart #{(keyword jarjestelma)})
        (async/<!! tapahtuman-odottaja)))))

(defonce start-lukko (Object.))

(def alkutila {::sonja {::yhteys-aloitettu? nil
                        ::uudelleen-kaynnistyksia 0
                        ::viestien-maara-kaynnistyksesta 0
                        ::uudelleen-kaynnistys-aika nil}
               ::itmf {::yhteys-aloitettu? nil
                       ::uudelleen-kaynnistyksia 0
                       ::viestien-maara-kaynnistyksesta 0
                       ::uudelleen-kaynnistys-aika nil}})

(defrecord UudelleenKaynnistaja [timeout-asetukset tapahtumien-tarkkailijat]
  component/Lifecycle
  (start [this]
    (locking start-lukko
      (when-not (contains? this ::sonja-yhteys-aloitettu-atom?)
        (let [this (assoc this ::tila (atom alkutila))]
          (if (asetukset/ominaisuus-kaytossa? :sonja-uudelleen-kaynnistys)
            (jmstarkkailu! "sonja" this)
            (log/info "Sonja uudelleen käynnistys ei ole käytössä"))
          (if (asetukset/ominaisuus-kaytossa? :itmf-uudelleen-kaynnistys)
            (jmstarkkailu! "itmf" this)
            (log/info "ITMF uudelleen käynnistys ei ole käytössä"))
          this))))
  (stop [this]
    (when-let [tapahtumien-tarkkailijat (:tapahtumien-tarkkailijat this)]
      (doseq [[_ tarkkailija] @tapahtumien-tarkkailijat]
        (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @tarkkailija))
      (reset! tapahtumien-tarkkailijat nil))
    (when-let [tila (::tila this)]
      (reset! tila alkutila))
    (dissoc this ::tila)))
