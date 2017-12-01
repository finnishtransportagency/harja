(ns harja.palvelin.palvelut.kanavat.kanavatoimenpiteet
  (:require [com.stuartsierra.component :as component]
            [specql.core :as specql]
            [specql.op :as op]
            [clojure.java.jdbc :as jdbc]
            [harja.id :as id]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as q-toimenpide]))

(defn hae-kanavatoimenpiteet [db user {urakka-id ::toimenpide/urakka-id
                                       sopimus-id ::toimenpide/sopimus-id
                                       alkupvm :alkupvm
                                       loppupvm :loppupvm
                                       toimenpidekoodi ::toimenpidekoodi/id
                                       tyyppi ::toimenpide/kanava-toimenpidetyyppi
                                       :as hakuehdot}]
  (assert urakka-id "Urakka-id puuttuu!")
  (case tyyppi
    :kokonaishintainen (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user urakka-id)
    :muutos-lisatyo (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-lisatyot user urakka-id))

  (let [tyyppi (when tyyppi (name tyyppi))]
    (q-toimenpide/hae-sopimuksen-toimenpiteet-aikavalilta
     db
     {:urakka urakka-id
      :sopimus sopimus-id
      :alkupvm alkupvm
      :loppupvm loppupvm
      :toimenpidekoodi toimenpidekoodi
      :tyyppi tyyppi})))

(defn- vaadi-rivit-kuuluvat-emoon* [rivit rivi-idt rivin-emo-id-avain vaadittu-emo-id]
  (let [emo-idt (set (keep rivin-emo-id-avain rivit))]
    (when (not= emo-idt #{vaadittu-emo-id})
      (throw (SecurityException. (str "Rivit " rivi-idt " eivät kuulu emoon " vaadittu-emo-id))))))


(defn vaadi-rivit-kuuluvat-emoon [db rivien-taulu rivin-emo-id-avain rivin-id-avain rivi-idt emo-id]
  (let [rivit (specql/fetch
               db
               rivien-taulu
               #{rivin-emo-id-avain rivin-id-avain}
               {rivin-id-avain (op/in rivi-idt)})]
    (println "rivit" rivit)
    (when (not-empty rivi-idt)
      (vaadi-rivit-kuuluvat-emoon* rivit rivi-idt rivin-emo-id-avain emo-id))))


(defn tallenna-kanavatoimenpiteen-hinnoittelu! [db user tiedot]
  (let [urakka-id (::toimenpide/urakka-id tiedot)
        toimenpide-id (::toimenpide/id tiedot)
        liita-tpid-mappeihin (fn [mapit k]
                               (mapv #(assoc % k toimenpide-id) mapit))]
    (assert urakka-id "Urakka-id puuttuu!")
    (oikeudet/vaadi-oikeus "hinnoittele-toimenpide" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id) ;; FIXME
    (vaadi-rivit-kuuluvat-emoon db ::toimenpide/kanava-toimenpide ::toimenpide/urakka-id ::toimenpide/id #{(::toimenpide/id tiedot)} urakka-id)
    (let [olemassa-olevat-hinta-idt (->> (keep ::hinta/id (::hinta/tallennettavat-hinnat tiedot))
                                         (filter id/id-olemassa?)
                                         (set))
          olemassa-olevat-tyo-idt (->> (keep ::tyo/id (::hinta/tallennettavat-tyot tiedot))
                                       (filter id/id-olemassa?)
                                       (set))]
      (vaadi-rivit-kuuluvat-emoon db ::hinta/toimenpiteen-hinta ::hinta/toimenpide-id ::hinta/id olemassa-olevat-hinta-idt toimenpide-id)
      (vaadi-rivit-kuuluvat-emoon db ::tyo/toimenpiteen-tyo ::tyo/toimenpide-id ::tyo/id olemassa-olevat-tyo-idt toimenpide-id))


    (jdbc/with-db-transaction [db db]
      (q-toimenpide/tallenna-toimenpiteen-omat-hinnat!
       {:db db
        :user user
        :hinnat (liita-tpid-mappeihin (::hinta/tallennettavat-hinnat tiedot) ::hinta/toimenpide-id)})
      (q-toimenpide/tallenna-toimenpiteen-tyot!
       {:db db
        :user user
        :tyot (liita-tpid-mappeihin (::tyo/tallennettavat-tyot tiedot) ::tyo/toimenpide-id)})
      (first (q-toimenpide/hae-kanavatoimenpiteet db {::toimenpide/id toimenpide-id})))))

(defn tarkista-kutsu [user urakka-id tyyppi]
  (assert urakka-id "Kanavatoimenpiteellä ei ole urakkaa.")
  (assert tyyppi "Kanavatoimenpiteellä ei ole tyyppiä.")
  (case tyyppi
    :kokonaishintainen (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user urakka-id)
    :muutos-lisatyo (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-lisatyot user urakka-id)))

(defn hae-kanavatoimenpiteet [db user {urakka-id ::toimenpide/urakka-id
                                       sopimus-id ::toimenpide/sopimus-id
                                       alkupvm :alkupvm
                                       loppupvm :loppupvm
                                       toimenpidekoodi ::toimenpidekoodi/id
                                       tyyppi ::toimenpide/kanava-toimenpidetyyppi}]

  (tarkista-kutsu user urakka-id tyyppi)
  (let [tyyppi (name tyyppi)]
    (q-toimenpide/hae-sopimuksen-toimenpiteet-aikavalilta
      db
      {:urakka urakka-id
       :sopimus sopimus-id
       :alkupvm alkupvm
       :loppupvm loppupvm
       :toimenpidekoodi toimenpidekoodi
       :tyyppi tyyppi})))

(defn tallenna-kanavatoimenpide [db user {tyyppi ::toimenpide/tyyppi
                                          urakka-id ::toimenpide/urakka-id
                                          :as kanavatoimenpide}]
  (tarkista-kutsu user urakka-id tyyppi)
  (q-toimenpide/tallenna-toimenpide db (:id user) kanavatoimenpide))

(defrecord Kanavatoimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-palvelu
     http
     :hae-kanavatoimenpiteet
     (fn [user hakuehdot]
       (hae-kanavatoimenpiteet db user hakuehdot))
     {:kysely-spec ::toimenpide/hae-kanavatoimenpiteet-kysely
      :vastaus-spec ::toimenpide/hae-kanavatoimenpiteet-vastaus})

    (julkaise-palvelu
     http
     :tallenna-kanavatoimenpiteen-hinnoittelu
     (fn [user hakuehdot]
       (tallenna-kanavatoimenpiteen-hinnoittelu! db user hakuehdot))
     {:kysely-spec ::toimenpide/tallenna-kanavatoimenpiteen-hinnoittelu-kysely
      :vastaus-spec ::toimenpide/tallenna-kanavatoimenpiteen-hinnoittelu-vastaus})

    (julkaise-palvelu
      http
      :tallenna-kanavatoimenpide
      (fn [user {toimenpide ::toimenpide/kanava-toimenpide
                 hakuehdot ::toimenpide/hae-kanavatoimenpiteet-kysely}]
        (tallenna-kanavatoimenpide db user toimenpide)
        (hae-kanavatoimenpiteet db user hakuehdot))
      {:kysely-spec ::toimenpide/tallenna-kanavatoimenpide-kutsu
       :vastaus-spec ::toimenpide/hae-kanavatoimenpiteet-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kanavatoimenpiteet
      :tallenna-kanavatoimenpiteen-hinnoittelu
      :tallenna-kanavatoimenpide
      )
    this))
