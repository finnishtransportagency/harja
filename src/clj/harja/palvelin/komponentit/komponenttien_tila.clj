(ns harja.palvelin.komponentit.komponenttien-tila
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]))

(defn- tallenna-jms-tila-cacheen [komponenttien-tila jarjestelma {:keys [palvelin payload]} timeout?]
  (let [jarjestelma-payloadista (and (map? payload)
                                     (ffirst payload))]
    (when (= jarjestelma jarjestelma-payloadista)
      (swap! komponenttien-tila
             (fn [kt]
               (let [jarjestelma-kw (and (string? jarjestelma)
                                         (keyword jarjestelma))
                     olioiden-tila (and (map? payload)
                                        (get payload jarjestelma))
                     jarkeva-payload? (map? olioiden-tila)
                     tilan-tarkastaminen-onnistui? (and jarkeva-payload?
                                                        (not (contains? olioiden-tila :virhe)))
                     tilan-tarkastaminen-epaonnistui? (and jarkeva-payload?
                                                           (contains? olioiden-tila :virhe))
                     harjaa-kaynnistetaan-uudestaan? (get-in kt [palvelin :harja :kaynnistetaan-uudestaan?])]
                 (cond
                   (not jarkeva-payload?)
                   (-> kt
                       (assoc-in [palvelin jarjestelma-kw] {:payload :tunnistamaton-tila})
                       (assoc-in [palvelin jarjestelma-kw :kaikki-ok?] false))
                   harjaa-kaynnistetaan-uudestaan?
                   (-> kt
                       (assoc-in [palvelin jarjestelma-kw] {:payload :harjaa-kaynnistetaan-uudestaan})
                       (assoc-in [palvelin jarjestelma-kw :kaikki-ok?] true))
                   tilan-tarkastaminen-onnistui?
                   (-> kt
                       (assoc-in [palvelin jarjestelma-kw] payload)
                       (assoc-in [palvelin jarjestelma-kw :kaikki-ok?] (if timeout?
                                                                         false
                                                                         (tapahtuma-tulkkaus/jmsyhteys-ok? olioiden-tila))))
                   tilan-tarkastaminen-epaonnistui?
                   (-> kt
                       (assoc-in [palvelin jarjestelma-kw] {:payload olioiden-tila})
                       (assoc-in [palvelin jarjestelma-kw :kaikki-ok?] false)))))))))

(defn- harjajarjestelman-restart [komponentin-tila tapahtuma {:keys [palvelin payload]}]
  (case tapahtuma
    :aloitus (swap! komponentin-tila
                    update-in
                    [palvelin :harja]
                    assoc
                    :kaynnistetaan-uudestaan? true
                    :kaynnistettavat-komponentit payload)
    :onnistui (swap! komponentin-tila
                     update-in
                     [palvelin :harja]
                     assoc
                     :kaynnistetaan-uudestaan? false
                     :kaynnistettavat-komponentit nil)
    :epaonnistui (swap! komponentin-tila
                        update-in
                        [palvelin :harja]
                        assoc
                        :kaynnistetaan-uudestaan? false
                        :kaynnistettavat-komponentit nil
                        :kaikki-ok? false
                        :viesti "Harjan uudelleen käynnistys epäonnistui")))

(defn- tallenna-jms-uudelleen-kaynnistamisen-tila-cacheen [komponenttien-tila jarjestelma {:keys [palvelin payload]}]
  (let [jarjestelma-payloadista (and (map? payload)
                                     (ffirst payload))]
    (when (= jarjestelma jarjestelma-payloadista)
      (let [jarjestelma-kw (and (string? jarjestelma)
                                (keyword jarjestelma))]
        (swap! komponenttien-tila
               (fn [kt]
                 (-> kt
                     (assoc-in [palvelin jarjestelma-kw] {:payload :uudelleen-kaynnistys-epaonnistui})
                     (assoc-in [palvelin jarjestelma-kw :kaikki-ok?] false))))))))

(defn- tallenna-dbn-tila-cacheen [komponenttien-tila {:keys [palvelin payload]} timeout?]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :db :kaikki-ok?] (if timeout? false payload)))))

(defn- tallenna-db-replikan-tila-cacheen [komponenttien-tila {:keys [palvelin payload]} timeout?]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :db-replica :kaikki-ok?] (if timeout? false payload)))))

(defn- tallenna-harjan-tila-cacheen [komponenttien-tila {:keys [palvelin payload]}]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :harja] payload))))

(defn- tyhjenna-cache! [komponenttien-tila]
  (reset! komponenttien-tila {}))

(defrecord KomponentinTila [timeout-asetukset komponenttien-tila]
  component/Lifecycle
  (start [this]
    (let [{sonja-asetukset :sonja
           itmf-asetukset :itmf
           db-asetukset :db
           db-replica-asetukset :db-replica} timeout-asetukset
          varoaika (* 5 1000)]
      (merge this
             (zipmap [::harja-kuuntelija ::harjajarjestelman-restart ::harjajarjestelman-restart-onnistui
                      ::harjajarjestelman-restart-epaonnistui ::sonja-kuuntelija ::itmf-kuuntelija
                      ::sonja-uudelleenkaynnistys-epaonnistui ::itmf-uudelleenkaynnistys-epaonnistui ::db-kuuntelija
                      ::db-replica-kuuntelija]
                     (tapahtuma-apurit/tarkkaile-tapahtumia :harja-tila {:tyyppi :viimeisin-per-palvelin} (partial tallenna-harjan-tila-cacheen komponenttien-tila)
                                                            :harjajarjestelman-restart {:tyyppi :perus} (partial harjajarjestelman-restart komponenttien-tila :aloitus)
                                                            :harjajarjestelman-restart-onnistui {:tyyppi :perus} (partial harjajarjestelman-restart komponenttien-tila :onnistui)
                                                            :harjajarjestelman-restart-epaonnistui {:tyyppi :perus} (partial harjajarjestelman-restart komponenttien-tila :epaonnistui)
                                                            :jms-tila {:tyyppi :viimeisin-per-palvelin :timeout (+ (or (:paivitystiheys-ms sonja-asetukset) 0) varoaika)} (partial tallenna-jms-tila-cacheen komponenttien-tila "sonja")
                                                            :jms-tila {:tyyppi :viimeisin-per-palvelin :timeout (+ (or (:paivitystiheys-ms itmf-asetukset) 0) varoaika)} (partial tallenna-jms-tila-cacheen komponenttien-tila "itmf")
                                                            :jms-uudelleenkaynnistys-epaonnistui {:tyyppi :perus} (partial tallenna-jms-uudelleen-kaynnistamisen-tila-cacheen "sonja" komponenttien-tila)
                                                            :jms-uudelleenkaynnistys-epaonnistui {:tyyppi :perus} (partial tallenna-jms-uudelleen-kaynnistamisen-tila-cacheen "itmf" komponenttien-tila)
                                                            :db-tila {:tyyppi :viimeisin :timeout (+ (or (:paivitystiheys-ms db-asetukset) 0) varoaika)} (partial tallenna-dbn-tila-cacheen komponenttien-tila)
                                                            :db-replica-tila {:tyyppi :viimeisin :timeout (+ (or (:paivitystiheys-ms db-replica-asetukset) 0) varoaika)} (partial tallenna-db-replikan-tila-cacheen komponenttien-tila))))))
  (stop [this]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harjajarjestelman-restart this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harjajarjestelman-restart-onnistui this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harjajarjestelman-restart-epaonnistui this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::sonja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::itmf-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::sonja-uudelleenkaynnistys-epaonnistui this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::itmf-uudelleenkaynnistys-epaonnistui this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::db-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::db-replica-kuuntelija this))
    (tyhjenna-cache! komponenttien-tila)
    (dissoc this ::harja-kuuntelija ::harjajarjestelman-restart ::harjajarjestelman-restart-onnistui
            ::harjajarjestelman-restart-epaonnistui ::sonja-kuuntelija ::itmf-kuuntelija
            ::sonja-uudelleenkaynnistys-epaonnistui ::itmf-uudelleenkaynnistys-epaonnistui ::db-kuuntelija
            ::db-replica-kuuntelija)))

(defn komponentin-tila [timeout-asetukset]
  (->KomponentinTila timeout-asetukset (atom {})))
