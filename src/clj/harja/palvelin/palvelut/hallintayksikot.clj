(ns harja.palvelin.palvelut.hallintayksikot
  "Palvelut organisaatioiden perustietojen ja urakoiden hakemiseksi.
  Ei oikeustarkistuksia, koska tiedot ovat julkisia."
  (:require [com.stuartsierra.component :as component]
            [clojure.spec :as s]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.hallintayksikot :as q]
            [harja.kyselyt.organisaatiot :as org-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.urakat :refer [hae-organisaation-urakat hallintayksikon-urakat]]
            [harja.geo :refer [muunna-pg-tulokset]]
            [harja.domain.oikeudet :as oikeudet]))

(s/def ::liikennemuoto #{:tie :vesi})

(def organisaatio-xf
  (map #(assoc % :tyyppi (keyword (:tyyppi %)))))

(defn hae-hallintayksikot
  "Palvelu, joka palauttaa halutun liikennemuodon hallintayksiköt."
  [db user tiedot]
  (oikeudet/ei-oikeustarkistusta!)
  (let [liikennemuoto (:liikennemuoto tiedot)]
    (into []
         (muunna-pg-tulokset :alue)
         (q/listaa-hallintayksikot-kulkumuodolle db (when liikennemuoto
                                                      (case liikennemuoto
                                                       :tie "T"
                                                       :vesi "V"
                                                       :rata "R"))))))


(defn hae-organisaatio
  "Palvelu, joka palauttaa organisaation tiedot id:llä."
  [db user org-id]
  (oikeudet/ei-oikeustarkistusta!)
  (let [o (first (into []
                       organisaatio-xf (org-q/hae-organisaatio db org-id)))
        organisaation-urakat (if (= :urakoitsija (:tyyppi o))
                               (map #(dissoc % :alue) (hae-organisaation-urakat db user org-id))
                               (map #(dissoc % :alue) (hallintayksikon-urakat db user org-id)))]
    (assoc o :urakat organisaation-urakat)))

(defrecord Hallintayksikot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hallintayksikot (fn [user tiedot]
                                         (hae-hallintayksikot (:db this) user tiedot))
                      {:kysely-spec (s/keys :req-un [::liikennemuoto])})
    (julkaise-palvelu (:http-palvelin this)
                      :hae-organisaatio (fn [user org-id]
                                          (hae-organisaatio (:db this) user org-id)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hallintayksikot)
    (poista-palvelu (:http-palvelin this) :hae-organisaatio)
    this))
