(ns harja.palvelin.ajastetut-tehtavat.laskutusyhteenvedot
  "Ajastettu tehtävä laskutusyhteenvetojen muodostamiseksi valmiiksi välimuistiin"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.laskutusyhteenveto :as q]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.periodic :as time-periodic]
            [chime :as chime]))

(defn- muodosta-laskutusyhteenveto [db alku loppu urakka]
  (let [hk-alku (pvm/luo-pvm (pvm/vuosi alku) 9 1)
        hk-loppu (pvm/luo-pvm (pvm/vuosi loppu) 8 30)]
    (q/hae-laskutusyhteenvedon-tiedot db {:urakka urakka
                                          :hk_alkupvm hk-alku
                                          :hk_loppupvm hk-loppu
                                          :aikavali_alkupvm alku
                                          :aikavali_loppupvm loppu})))

(defn- muodosta-laskutusyhteenvedot [db]
  (let [nyt (pvm/nyt)
        vuosi (pvm/vuosi nyt)
        kk (- (pvm/kuukausi nyt) 2)
        viimeinen-paiva (t/day (t/last-day-of-the-month vuosi (inc kk)))
        alku (pvm/luo-pvm vuosi kk 1)
        loppu (pvm/luo-pvm vuosi kk viimeinen-paiva)]
    (doseq [{:keys [id nimi]} (q/hae-urakat-joille-laskutusyhteenveto-voidaan-tehda
                               db {:alku alku :loppu loppu})]
      (log/info "Muodostetaan laskutusyhteenveto valmiiksi urakalle: " nimi)
      (lukot/aja-lukon-kanssa
       db (str "laskutusyhteenveto:" id)
       #(try
          (muodosta-laskutusyhteenveto db alku loppu id)
          (catch Throwable t
            (log/error t "Virhe muodostettaessa laskutusyhteenvetoa, urakka: " id ", aikavali "
                       alku " -- " loppu)))))))

(defn- ajasta []
  (let [now (t/now)]
    (chime/chime-at (time-periodic/periodic-seq
                     (pvm/suomen-aikavyohykkeessa
                      (t/date-time (t/year now) (t/month now) (t/day now)
                                   4 30))
                     (t/days 1)))))

(defrecord LaskutusyhteenvetojenMuodostus []
  component/Lifecycle
  (start [this]
    (assoc this ::laskutusyhteenvetojen-ajastus
           (ajasta)))
  (stop [{poista ::laskutusyhteenvetojen-ajastus :as this}]
    (poista)
    (dissoc this ::laskutusyhteenvetojen-ajastus)))
