(ns harja.palvelin.palvelut.haku
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]

            [harja.kyselyt.urakat :as ur-q]
            [harja.kyselyt.kayttajat :as k-q]
            [harja.kyselyt.hallintayksikot :as org-q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]))

(defn hae-harjasta
  "Palvelu, joka hakee Harjasta hakutermin avulla."
  [db user hakutermi]
  (oikeudet/ei-oikeustarkistusta!) ;urakoitsijan osalta oikeustarkistus tehdään alla filterissä ja SQL-kyselyssä
  (let [termi (str "%" hakutermi "%")
        kayttajan-org (:organisaatio user)
        loytyneet-urakat (into []
                               (filter #(if (= "urakoitsija" (:tyyppi kayttajan-org))
                                         (oikeudet/voi-lukea? oikeudet/urakat (:id %) user)
                                         true))
                               (map #(assoc % :tyyppi :urakka
                                              :hakusanat (str (:nimi %) ", " (:sampoid %)))
                                    (ur-q/hae-urakoiden-tunnistetiedot db termi
                                                                       (name (:tyyppi kayttajan-org))
                                                                       (:id kayttajan-org))))
        loytyneet-kayttajat (into []
                                  (map #(assoc % :tyyppi :kayttaja
                                                 :hakusanat (if (:jarjestelmasta %)
                                                              (str "Järjestelmäkäyttäjä: "(:kayttajanimi %))
                                                              (clojure.string/trimr (str (:etunimi %) " " (:sukunimi %) ", " (:org_nimi %)))))
                                       (k-q/hae-kayttajien-tunnistetiedot db termi)))
        loytyneet-organisaatiot (into []
                                      (map #(assoc % :tyyppi :organisaatio
                                                     :hakusanat (str (when (:lyhenne %) (str (:lyhenne %) " "))
                                                                  (:nimi %) ", " (:organisaatiotyyppi %)))
                                           (org-q/hae-organisaation-tunnistetiedot db termi)))
        tulokset (into []
                       (concat loytyneet-urakat loytyneet-kayttajat loytyneet-organisaatiot))]
    tulokset))

(defn hae-kayttajan-tiedot
  "Hakee käyttäjän tarkemmat tiedot muokkausnäkymää varten."
  [db user kayttaja-id]
  (kayttajatiedot/hae-kayttaja db kayttaja-id))

(defrecord Haku []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :hae
                        (fn [user hakutermi]
                          (hae-harjasta (:db this) user hakutermi)))
      (julkaise-palvelu :hae-kayttajan-tiedot
                        (fn [user kayttaja-id]
                          (hae-kayttajan-tiedot (:db this) user kayttaja-id))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this) :hae :hae-kayttajan-tiedot)
    this))
