(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu
  (:require [certifiable.log :as log]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-kustannussuunnitelman-tiedot [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "hae-kustannussuunnitelman-tiedot :: tiedot: " tiedot)
  (let [;; HAetaan urakan toimenpiteet
        toimenpiteet (hae-urakan-toimenpiteet db urakka-id)
        k {:urakka-id urakka-id
           :kustannussuunnitelma {:kilpailutettavat-hankinnat {:toimenpiteet [{:nimi "Talvihoito"
                                                                               :alkukausi 400
                                                                               :loppukausi 500
                                                                               :pysyvat-muutokset "Ei muutoksia"
                                                                               :yhteensa 900
                                                                               }]}}}]
    k))

(defn tallenna-kilpailutettavat-hankinnat [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-kilpailutettavat-hankinnat :: tiedot: " tiedot)
  (let []
    (hae-kustannussuunnitelman-tiedot db kayttaja (:urakka-id tiedot))))


(defrecord UusiKustannussuunnitelmaPalvelu []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-kustannussuunnitelman-tiedot
      (fn [user tiedot]
        (hae-kustannussuunnitelman-tiedot (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
      :tallenna-kilpailutettavat-hankinnat
      (fn [user tiedot]
        (tallenna-kilpailutettavat-hankinnat (:db this) user tiedot)))

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-kustannussuunnitelman-tiedot
      :tallenna-kilpailutettavat-hankinnat)
    this))
