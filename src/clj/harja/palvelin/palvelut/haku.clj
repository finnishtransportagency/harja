(ns harja.palvelin.palvelut.haku
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]

            [harja.kyselyt.urakat :as ur-q]
            [harja.kyselyt.kayttajat :as k-q]
            [harja.kyselyt.hallintayksikot :as org-q]))

(defn hae-harjasta
  "Palvelu, joka hakee Harjasta hakutermin avulla."
  [db user hakutermi]
  ;; fixme: hanskaa oikeudet. Tilaajan käyttäjille palautetaan kaikki sisältö,
  ;; urakoitsijalle vain oman firmansa sisältö
  (let [termi (str "%" hakutermi "%")
        loytyneet-urakat (into []
                        (map #(assoc % :tyyppi :urakka)
                          (ur-q/hae-urakoiden-tunnistetiedot db termi)))
     loytyneet-kayttajat (into []
                           (map #(assoc % :tyyppi :kayttaja)
                             (k-q/hae-kayttajien-tunnistetiedot db termi)))
     loytyneet-organisaatiot (into []
                           (map #(assoc % :tyyppi :organisaatio)
                             (org-q/hae-organisaation-tunnistetiedot db termi)))
     tulokset (into []
                (concat loytyneet-urakat loytyneet-kayttajat loytyneet-organisaatiot))
     _ (log/info "haun tulokset" tulokset)]
    tulokset))

(defrecord Haku []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :hae (fn [user hakutermi]
                    (hae-harjasta (:db this) user hakutermi)))
      )
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae)
    this))


