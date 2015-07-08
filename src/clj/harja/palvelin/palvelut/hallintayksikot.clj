(ns harja.palvelin.palvelut.hallintayksikot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.hallintayksikot :as q]
            [harja.kyselyt.organisaatiot :as org-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.kayttajat :refer [organisaatio-xf]]
            [ harja.palvelin.palvelut.urakat :refer [hae-organisaation-urakat]]
            [harja.geo :refer [muunna-pg-tulokset]]))


(defn hae-hallintayksikot
  "Palvelu, joka palauttaa halutun liikennemuodon hallintayksiköt."
  [db user liikennemuoto]
  (into []
        (muunna-pg-tulokset :alue)
        (q/listaa-hallintayksikot-kulkumuodolle db (case liikennemuoto
                                                     :tie "T"
                                                     :vesi "V"
                                                     :rata "R"))))


(defn hae-organisaatio
  "Palvelu, joka palauttaa organisaation tiedot id:llä."
  [db user org-id]
  ;; FIXME: oikeustarkistuksia ei mietitty
  (let [o (first (into []
                       organisaatio-xf (org-q/hae-organisaatio db org-id)))
        organisaation-urakat (map #(dissoc % :alue) (hae-organisaation-urakat db user org-id))]
    (assoc o :urakat organisaation-urakat)))

(defrecord Hallintayksikot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hallintayksikot (fn [user liikennemuoto]
                                         (hae-hallintayksikot (:db this) user liikennemuoto)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-organisaatio (fn [user org-id]
                                          (hae-organisaatio (:db this) user org-id)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hallintayksikot)
    (poista-palvelu (:http-palvelin this) :hae-organisaatio)
    this))
