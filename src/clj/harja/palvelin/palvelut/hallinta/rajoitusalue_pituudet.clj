(ns harja.palvelin.palvelut.hallinta.rajoitusalue-pituudet
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu :as suolarajoitus-palvelu]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-rajoitusalueiden-pituudet [db user]
  (log/debug "hae-rajoitusalueiden-pituudet")
  (let [rajoitusalueet (suolarajoitus-kyselyt/hae-rajoitusalueiden-pituudet db)
        numero (atom 1)
        uudelleen-lasketut-pituudet (keep
                                      (fn [rajoitusalue]
                                        (let [_ (log/debug "Käsittelyssä " @numero "/" (count rajoitusalueet))
                                              _ (reset! numero (inc @numero))
                                              tiedot (suolarajoitus-palvelu/hae-tierekisterin-tiedot db user rajoitusalue)]
                                          (when (not= (:pituus-kannasta rajoitusalue) (:pituus tiedot))
                                            (merge rajoitusalue {:pituus-laskettu (:pituus tiedot)
                                                                 :ajoradan-pituus-laskettu (:ajoratojen_pituus tiedot)
                                                                 :pohjavesialueet (:pohjavesialueet tiedot)
                                                                 :ei-tasmaa (when (not= (:pituus-kannasta rajoitusalue) (:pituus tiedot))
                                                                              true)}))))
                                      rajoitusalueet)
        _ (log/debug "eri mittaisia rajoitusalueita löydettiin: " uudelleen-lasketut-pituudet " kpl")]
    uudelleen-lasketut-pituudet))

(defrecord RajoitusaluePituudet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-rajoitusalueiden-pituudet
      (fn [user _]
        (hae-rajoitusalueiden-pituudet (:db this) user)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-rajoitusalueiden-pituudet)
    this))
