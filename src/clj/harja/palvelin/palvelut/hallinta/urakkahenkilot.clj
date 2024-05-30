(ns harja.palvelin.palvelut.hallinta.urakkahenkilot
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot-q]
            [clojure.string :as str]))

(defn- kayttajan-rooli-str
  "Palauttaa vastuuhenkilön/urakanvalvojan roolin ihmisluettavassa muodossa"
  [{:keys [rooli ensisijainen toissijainen-varahenkilo]}]
  (str/join
    " "
    (keep identity
      [(cond
         ensisijainen nil
         toissijainen-varahenkilo "(Toissijainen varahenkilö)"
         :else "(Varahenkilö)")])))

(defn hae-urakkahenkilot [db kayttaja tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-urakkahenkilot kayttaja)
  (yhteyshenkilot-q/hae-vastuuhenkilot-hallinta db tiedot)
  (map (fn [{:keys [etunimi sukunimi puhelin sahkoposti rooli]}]
         {:nimi (str/join " " [etunimi sukunimi])
          :puhelin puhelin
          :sahkoposti sahkoposti
          :rooli rooli
          })))

(defrecord UrakkaHenkilotHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakkahenkilot
      (fn [kayttaja tiedot]
        (hae-urakkahenkilot db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakkahenkilot)
    this))
