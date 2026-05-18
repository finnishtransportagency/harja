(ns harja.palvelin.palvelut.haku
  (:require [com.stuartsierra.component :as component]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.urakat :as ur-q]
            [harja.kyselyt.hallintayksikot :as org-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as urakka-domain]))

(defn hae-harjasta
  "Palvelu, joka hakee Harjasta hakutermin avulla."
  [db user hakutermi]
  (oikeudet/ei-oikeustarkistusta!) ;urakoitsijan osalta oikeustarkistus tehdään organisaation kautta SQL-kyselyssä
  (let [termi (str "%" hakutermi "%")
        kayttajan-org (:organisaatio user)
        kayttajan-org (if (roolit/jvh? user)                ;; jvh:lla ei ole aina organisaatiota. Aseta se liikennevirastoksi
                        (assoc kayttajan-org :tyyppi :liikennevirasto)
                        kayttajan-org)
        loytyneet-urakat (when (or kayttajan-org (roolit/jvh? user))                ;sallitaan haku vain jos on organisaatio tiedossa (oikeustarkistus) tai jvh
                           (into []
                                (filter #(if (= "urakoitsija" (:tyyppi kayttajan-org))
                                           (oikeudet/voi-lukea? oikeudet/urakat (:id %) user)
                                           true))
                             (map #(assoc % :tyyppi :urakka
                                     :hakusanat (str (:id %) " " (:nimi %) ", " (:sampoid %))
                                     :format (str (:nimi %) ", " (:sampoid %)
                                               " (" (urakka-domain/urakkatyyppi->otsikko (keyword (:urakkatyyppi %)))
                                               (when-not (= "käynnissä" (:urakan_ajankohtaisuus %))
                                                 (str ", " (:urakan_ajankohtaisuus %)))
                                               ")"))
                                     (ur-q/hae-urakoiden-tunnistetiedot db
                                       {:termi termi
                                        :kayttajan_org_tyyppi (name (:tyyppi kayttajan-org))
                                        :kayttajan_org_id (:id kayttajan-org)
                                        :numero (when (re-matches (re-pattern "\\d+") hakutermi)
                                                  (Integer/parseInt hakutermi))}))))
        loytyneet-organisaatiot (when (or kayttajan-org (roolit/jvh? user))         ;sallitaan haku vain jos on organisaatio tiedossa (oikeustarkistus) tai jvh
                                  (into []
                                        (map #(assoc % :tyyppi :organisaatio
                                                       :hakusanat (str (when (:lyhenne %) (str (:lyhenne %) " "))
                                                                       (:nimi %) ", " (:organisaatiotyyppi %)))
                                             (org-q/hae-organisaation-tunnistetiedot db termi))))
        tulokset (into []
                       (concat loytyneet-urakat loytyneet-organisaatiot))]
    tulokset))

(defrecord Haku []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :hae
                        (fn [user hakutermi]
                          (hae-harjasta (:db this) user hakutermi))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this) :hae)
    this))
