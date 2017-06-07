(ns harja.palvelin.integraatiot.reimari.reimari-komponentti
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.reimari.toimenpidehaku :as toimenpidehaku]
            [harja.palvelin.integraatiot.reimari.komponenttihaku :as komponenttihaku]
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

(defrecord Reimari [pohja-url kayttajatunnus salasana paivittainen-tphakuaika]
  component/Lifecycle
  (start [this]
    (log/info "Käynnistetään Reimari-komponentti, pohja-url" pohja-url)
    (if paivittainen-tphakuaika
      (assoc this
             :ajastus-peruutus-fn (ajastettu-tehtava/ajasta-paivittain
                                   paivittainen-tphakuaika
                                   (fn [& args] (hae-toimenpiteet this))))
      ;; else
      this))
  (stop [this]
    (log/debug "Sammutetaan Reimari-komponentti")
    (when paivittainen-tphakuaika
      (apply (:ajastus-peruutus-fn this) []))
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
    (komponenttihaku/hae-turvalaitekomponentit (:db this) (:integraatioloki this) pohja-url kayttajatunnus salasana)))
