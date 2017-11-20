(ns harja.palvelin.palvelut.kanavat.kanavatoimenpiteet
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as urakka]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as q-kanavan-toimenpide]))

(defn hae-kanavatoimenpiteet [db user {urakka-id ::kanavan-toimenpide/urakka-id
                                       sopimus-id ::kanavan-toimenpide/sopimus-id
                                       alkupvm :alkupvm
                                       loppupvm :loppupvm
                                       toimenpidekoodi ::toimenpidekoodi/id
                                       tyyppi ::kanavan-toimenpide/kanava-toimenpidetyyppi
                                       :as hakuehdot}]

  (assert urakka-id "Urakka-id puuttuu!")
  (case tyyppi
    :kokonaishintainen (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user urakka-id)
    :muutos-lisatyo (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-lisatyot user urakka-id))

  (let [tyyppi (when tyyppi (name tyyppi))]
    (q-kanavan-toimenpide/hae-sopimuksen-toimenpiteet-aikavalilta
      db
      {:urakka urakka-id
       :sopimus sopimus-id
       :alkupvm alkupvm
       :loppupvm loppupvm
       :toimenpidekoodi toimenpidekoodi
       :tyyppi tyyppi})))

(defn siirra-kanavatoimenpiteet [db user tiedot]
  (let [urakka-id (::kanavan-toimenpide/urakka-id tiedot)
        siirto-kokonaishintaisiin? (= (::kanavan-toimenpide/tyyppi tiedot) :kokonaishintainen)]
    (assert urakka-id "Urakka-id puuttuu!")
    (if siirto-kokonaishintaisiin?
      (oikeudet/vaadi-oikeus "siirrä-kokonaishintaisiin" oikeudet/urakat-kanavat-lisatyot user urakka-id)
      (oikeudet/vaadi-oikeus "siirrä-muutos-ja-lisätöihin" oikeudet/urakat-kanavat-kokonaishintaiset user urakka-id))
    (q-kanavan-toimenpide/vaadi-toimenpiteet-kuuluvat-urakkaan db (::kanavan-toimenpide/toimenpide-idt tiedot) urakka-id)
    (q-kanavan-toimenpide/paivita-toimenpiteiden-tyyppi db (::kanavan-toimenpide/toimenpide-idt tiedot) (::kanavan-toimenpide/tyyppi tiedot))
    (::kanavan-toimenpide/toimenpide-idt tiedot)))

(defrecord Kanavatoimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-palvelu
      http
      :hae-kanavatoimenpiteet
      (fn [user hakuehdot]
        (hae-kanavatoimenpiteet db user hakuehdot))
      {:kysely-spec ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely
       :vastaus-spec ::kanavan-toimenpide/hae-kanavatoimenpiteet-vastaus})
    (julkaise-palvelu
      http
      :siirra-kanavatoimenpiteet
      (fn [user hakuehdot]
        (siirra-kanavatoimenpiteet db user hakuehdot))
      {:kysely-spec ::kanavan-toimenpide/siirra-kanavatoimenpiteet-kysely
       :vastaus-spec ::kanavan-toimenpide/siirra-kanavatoimenpiteet-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kanavat-ja-kohteet)
    this))