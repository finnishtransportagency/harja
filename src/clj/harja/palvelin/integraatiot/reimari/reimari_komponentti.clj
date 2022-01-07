(ns harja.palvelin.integraatiot.reimari.reimari-komponentti
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.toimenpidehaku :as toimenpidehaku]
            [harja.palvelin.integraatiot.reimari.komponenttihaku :as komponenttihaku]
            [harja.palvelin.integraatiot.reimari.vikahaku :as vikahaku]
            [harja.palvelin.integraatiot.reimari.turvalaiteryhmahaku :as turvalaiteryhmahaku]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukko]))


(defprotocol HaeToimenpiteet
  (hae-toimenpiteet [this]))

(defprotocol HaeKomponenttiTyypit
  (hae-komponenttityypit [this]))

(defprotocol HaeTurvalaiteKomponentit
  (hae-turvalaitekomponentit [this]))

(defprotocol HaeViat
  (hae-viat [this]))

(defprotocol HaeTurvalaiteryhmat
  (hae-turvalaiteryhmat [this]))

(def hakuajat
  {:toimenpiteet [2 0 0]
   :komponenttityypit [2 5 0]
   :turvalaitekomponentit [2 10 0]
   :viat [2 15 0]
   :turvalaiteryhmat [2 20 0]})

(defrecord Reimari [pohja-url kayttajatunnus salasana]
  component/Lifecycle
  (start [this]
    (log/info "Käynnistetään Reimari-komponentti, pohja-url" pohja-url)
    (assoc this
           :tp-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:toimenpiteet hakuajat)
                                                                        (fn [& args] (hae-toimenpiteet this)))
           :kt-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:komponenttityypit hakuajat)
                                                                        (fn [& args] (hae-komponenttityypit this)))
           :tlk-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:turvalaitekomponentit hakuajat)
                                                                         (fn [& args] (hae-turvalaitekomponentit this)))
           :viat-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:viat hakuajat)
                                                                          (fn [& args] (hae-viat this)))
           :turvalaiteryhmat-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:turvalaiteryhmat hakuajat)
                                                                                      (fn [& args] (hae-turvalaiteryhmat this)))))
  (stop [this]
    (log/debug "Sammutetaan Reimari-komponentti")
    (doseq [k [:tp-ajastus-peruutus-fn :kt-ajastus-peruutus-fn :tlk-ajastus-peruutus-fn
               :viat-ajastus-peruutus-fn :turvalaiteryhmat-ajastus-peruutus-fn]]
      (when-let [peru-fn (get this k)]
        (peru-fn)))
    this)

  HaeToimenpiteet
  (hae-toimenpiteet [this]
    (log/debug "Reimari HaeToimenpiteet kutsuttu")
    (toimenpidehaku/hae-toimenpiteet (:db this) (:integraatioloki this) pohja-url kayttajatunnus salasana))

  HaeKomponenttiTyypit
  (hae-komponenttityypit [this]
    (log/debug "Reimari HaeKomponenttiTyypit kutsuttu")
    (komponenttihaku/hae-komponenttityypit (:db this) (:integraatioloki this) pohja-url kayttajatunnus salasana))

  HaeTurvalaiteKomponentit
  (hae-turvalaitekomponentit [this]
    (log/debug "Reimari HaeTurvalaiteKomponentit kutsuttu")
    (komponenttihaku/hae-turvalaitekomponentit (:db this) (:integraatioloki this) pohja-url kayttajatunnus salasana))

  HaeViat
  (hae-viat [this]
    (log/debug "Reimari HaeViat kutsuttu")
    (vikahaku/hae-viat (:db this) (:integraatioloki this) pohja-url kayttajatunnus salasana))

   HaeTurvalaiteryhmat
    (hae-turvalaiteryhmat [this]
     (log/debug "Reimari HaeTurvalaiteryhmat kutsuttu")
     (turvalaiteryhmahaku/hae-turvalaiteryhmat (:db this) (:integraatioloki this) pohja-url kayttajatunnus salasana)))
