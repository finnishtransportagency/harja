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
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.pvm :as pvm]))


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

;; Integraatiot poistettu käytöstä kesällä 2022. Ajastukset nollattu.
(def hakuajat
  {:toimenpiteet nil
   :komponenttityypit nil
   :turvalaitekomponentit nil
   :viat nil
   :turvalaiteryhmat nil})

(defrecord Reimari [pohja-url kayttajatunnus salasana]
  component/Lifecycle
  (start [this]
    (log/info "Käynnistetään Reimari-komponentti, pohja-url" pohja-url)
    (assoc this
           :tp-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:toimenpiteet hakuajat)
                                     (do
                                       (log/info "ajasta-paivittain :: hae-toimenpiteet :: Alkaa " (pvm/nyt))
                                       (fn [& args] (hae-toimenpiteet this))))
           :kt-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:komponenttityypit hakuajat)
                                     (do
                                       (log/info "ajasta-paivittain :: hae-komponenttityypit :: Alkaa " (pvm/nyt))
                                       (fn [& args] (hae-komponenttityypit this))))
           :tlk-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:turvalaitekomponentit hakuajat)
                                      (do
                                        (log/info "ajasta-paivittain :: hae-turvalaitekomponentit :: Alkaa " (pvm/nyt))
                                        (fn [& args] (hae-turvalaitekomponentit this))))
           :viat-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:viat hakuajat)
                                       (do
                                         (log/info "ajasta-paivittain :: hae-viat :: Alkaa " (pvm/nyt))
                                         (fn [& args] (hae-viat this))))
           :turvalaiteryhmat-ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain (:turvalaiteryhmat hakuajat)
                                                   (do
                                                     (log/info "ajasta-paivittain :: hae-turvalaiteryhmat :: Alkaa " (pvm/nyt))
                                                     (fn [& args] (hae-turvalaiteryhmat this))))))
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
