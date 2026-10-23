(ns harja.palvelin.palvelut.hallinta.raporttityokalu-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.ajastetut-tehtavat.kustannusarvioiden-toteumat :as kustannusarvioidut-toteumat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konversio-kyselyt]
            [harja.kyselyt.materiaalit :as materiaali-kyselyt]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [taoensso.timbre :as log]))


(defn- paivita-materiaalicachet-urakalle
  "Päivittää sopimuksen materiaalicachet valitulle urakalle ja kuukaudelle
   Käytetään, kun on tarve päivittää materiaalicachet tiettyyn urakkaan liittyen."
  [db kayttaja {:keys [urakka-id alkupvm loppupvm] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-toteumatyokalu kayttaja)
  (log/debug "paivita-materiaalicachet-urakalle käynnistetty!" (pr-str tiedot))
  (let [sopimus-id (urakka-kyselyt/urakan-paasopimus-id db urakka-id)
        alku-inst (pvm/dateksi alkupvm)
        loppu-inst (pvm/dateksi loppupvm)
        paiva-vali (pvm/paivat-aikavalissa alku-inst loppu-inst)
        ;; Materiaalicachet päivitetään jokaiselle päivälle erikseen
        _ (doseq [pv paiva-vali]
            (materiaali-kyselyt/paivita-sopimuksen-materiaalin-kaytto db {:sopimus sopimus-id
                                                                          :alkupvm (konversio-kyselyt/joda-datetime->sql-timestamp pv)
                                                                          :urakkaid urakka-id}))
        _ (materiaali-kyselyt/paivita-urakan-materiaalin-kaytto-hoitoluokittain db {:urakka urakka-id
                                                                                    :alkupvm alku-inst
                                                                                    :loppupvm loppu-inst})]
    {:status "OK"
     :urakka-id urakka-id
     :viesti "Materiaalicachet päivitetty urakalle"}))

(defrecord RaporttityokaluHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :paivita-materiaalicachet-urakalle
      (fn [kayttaja tiedot]
        (paivita-materiaalicachet-urakalle db kayttaja tiedot)))

    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :paivita-materiaalicachet-urakalle)
    this))
