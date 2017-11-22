(ns harja.palvelin.palvelut.kanavat.kanavatoimenpiteet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as q-kanavan-toimenpide]))

(defn tarkista-kutsu [user urakka-id tyyppi]
  (assert urakka-id "Kanavatoimenpiteellä ei ole urakkaa.")
  (assert tyyppi "Kanavatoimenpiteellä ei ole tyyppiä.")
  (case tyyppi
    :kokonaishintainen (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user urakka-id)
    :muutos-lisatyo (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-lisatyot user urakka-id)))

(defn hae-kanavatoimenpiteet [db user {urakka-id ::kanavan-toimenpide/urakka-id
                                       sopimus-id ::kanavan-toimenpide/sopimus-id
                                       alkupvm :alkupvm
                                       loppupvm :loppupvm
                                       toimenpidekoodi ::toimenpidekoodi/id
                                       tyyppi ::kanavan-toimenpide/kanava-toimenpidetyyppi}]

  (tarkista-kutsu user urakka-id tyyppi)
  (let [tyyppi (name tyyppi)]
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

(defn tallenna-kanavatoimenpide [db user {tyyppi ::kanavan-toimenpide/tyyppi
                                          urakka-id ::kanavan-toimenpide/urakka-id
                                          :as kanavatoimenpide}]
  (tarkista-kutsu user urakka-id tyyppi)
  (q-kanavan-toimenpide/tallenna-toimenpide db (:id user) kanavatoimenpide))

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
    (julkaise-palvelu
      http
      :tallenna-kanavatoimenpide
      (fn [user {toimenpide ::kanavan-toimenpide/kanava-toimenpide
                 hakuehdot ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely}]
        (tallenna-kanavatoimenpide db user toimenpide)
        (hae-kanavatoimenpiteet db user hakuehdot))
      {:kysely-spec ::kanavan-toimenpide/tallenna-kanavatoimenpide-kutsu
       :vastaus-spec ::kanavan-toimenpide/hae-kanavatoimenpiteet-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kanavat-ja-kohteet
      :siirra-kanavatoimenpiteet
      :tallenna-kanavatoimenpide)
    this))