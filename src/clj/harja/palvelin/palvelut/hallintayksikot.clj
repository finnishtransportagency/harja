(ns harja.palvelin.palvelut.hallintayksikot
  "Palvelut organisaatioiden perustietojen ja urakoiden hakemiseksi.
  Ei oikeustarkistuksia, koska tiedot ovat julkisia."
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.hallintayksikot :as q]
            [harja.kyselyt.organisaatiot :as org-q]
            [harja.palvelin.palvelut.urakat :refer [hae-organisaation-urakat elinvoimakeskuksen-urakat]]
            [harja.geo :refer [muunna-pg-tulokset]]
            [harja.domain.oikeudet :as oikeudet]))

(s/def ::liikennemuoto (s/nilable #{:tie :vesi}))

(def organisaatio-xf
  (map #(assoc % :tyyppi (keyword (:tyyppi %)))))

(defn hae-elinvoimakeskukset
  "Palvelu, joka palauttaa halutun liikennemuodon elinvoimakeskukset."
  [db user tiedot]
  (oikeudet/ei-oikeustarkistusta!)
  (let [liikennemuoto (:liikennemuoto tiedot)
        evkt (q/listaa-elinvoimakeskukset-kulkumuodolle db (when liikennemuoto
                                                             (case liikennemuoto
                                                               :tie "T"
                                                               :vesi "V"
                                                               :rata "R")))]
    (into []
      (muunna-pg-tulokset :alue)
      evkt)))


(defn hae-organisaatio
  "Palvelu, joka palauttaa organisaation tiedot id:llä."
  [db user org-id]
  (oikeudet/ei-oikeustarkistusta!)
  (let [o (first (into []
                       organisaatio-xf (org-q/hae-organisaatio db org-id)))
        organisaation-urakat (if (= :urakoitsija (:tyyppi o))
                               (map #(dissoc % :alue) (hae-organisaation-urakat db user org-id))
                               (map #(dissoc % :alue) (elinvoimakeskuksen-urakat db user org-id)))]
    (assoc o :urakat organisaation-urakat)))

(defrecord Hallintayksikot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :elinvoimakeskukset (fn [user tiedot]
                            (hae-elinvoimakeskukset (:db this) user tiedot))
      {:kysely-spec (s/keys :req-un [::liikennemuoto])})
    (julkaise-palvelu (:http-palvelin this)
      :hae-organisaatio (fn [user org-id]
                          (hae-organisaatio (:db this) user org-id)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :elinvoimakeskukset)
    (poista-palvelu (:http-palvelin this) :hae-organisaatio)
    this))
