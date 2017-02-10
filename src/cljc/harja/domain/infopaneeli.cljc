(ns harja.domain.infopaneeli
  "Infopaneelin tuloksien spec määrittelyt"
  (:require [clojure.spec :as s]
            [harja.domain.tierekisteri :as tr]))

(defmulti infopaneeli-skeema :tyyppi-kartalla)

;; FIXME: määrittele eri skeemat
;; Nämä pitäisi määritellä omissa domain asioissa tai
;; määritellä vaaditut avaimet suoraan täällä

(defmethod infopaneeli-skeema :tyokone [_]
  (s/keys))
(defmethod infopaneeli-skeema :toimenpidepyynto [_]
  (s/keys))
(defmethod infopaneeli-skeema :tiedoitus [_]
  (s/keys))
(defmethod infopaneeli-skeema :kysely [_]
  (s/keys))
(defmethod infopaneeli-skeema :varustetoteuma [_]
  (s/keys))
(defmethod infopaneeli-skeema :paallystys [_]
  (s/keys))
(defmethod infopaneeli-skeema :paikkaus [_]
  (s/keys))
(defmethod infopaneeli-skeema :turvallisuuspoikkeama [_]
  (s/keys))
(defmethod infopaneeli-skeema :tarkastus [_]
  (s/keys))
(defmethod infopaneeli-skeema :laatupoikkeama [_]
  (s/keys))
(defmethod infopaneeli-skeema :suljettu-tieosuus [_]
  (s/keys))
(defmethod infopaneeli-skeema :toteuma [_]
  (s/keys))
(defmethod infopaneeli-skeema :silta [_]
  (s/keys))
(defmethod infopaneeli-skeema :tietyomaa [_]
  (s/keys))

;; Infopaneelin tuloksen spec päätetään :tyyppi-kartalla avaimen perusteella
(s/def ::tulos (s/multi-spec infopaneeli-skeema :tyyppi-kartalla))
