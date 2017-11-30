(ns harja.palvelin.palvelut.kanavat.kanavatoimenpiteet
  (:require [com.stuartsierra.component :as component]
            [clojure.set :as set]
            [taoensso.timbre :as log]
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
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodit]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as q-toimenpide]
            [clojure.java.jdbc :as jdbc]))

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
      (first (q-toimenpide/hae-kanavatoimenpiteet* db {::toimenpide/id toimenpide-id})))))

(defn- tarkista-kutsu [user urakka-id tyyppi]
  (assert urakka-id "Kanavatoimenpiteellä ei ole urakkaa.")
  (assert tyyppi "Kanavatoimenpiteellä ei ole tyyppiä.")
  (case tyyppi
    :kokonaishintainen (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user urakka-id)
    :muutos-lisatyo (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-lisatyot user urakka-id)))

(defn- tehtava-paivitetaan? [hintatyyppi tehtava]
  (let [hintatyyppi-loytyi? (some #(when (= hintatyyppi %) %)
                                  (get-in tehtava [::toimenpide/toimenpidekoodi ::toimenpidekoodi/hinnoittelu]))]
    (not hintatyyppi-loytyi?)))

(defn hae-kanavatoimenpiteet [db user {urakka-id ::toimenpide/urakka-id
                                       sopimus-id ::toimenpide/sopimus-id
                                       alkupvm :alkupvm loppupvm :loppupvm
                                       toimenpidekoodi ::toimenpidekoodi/id
                                       tyyppi ::toimenpide/kanava-toimenpidetyyppi
                                       kohde ::toimenpide/kohde-id}]
  (tarkista-kutsu user urakka-id tyyppi)
  (let [tyyppi (name tyyppi)]
    (q-toimenpide/hae-kanavatoimenpiteet
      db
      {:urakka urakka-id
       :sopimus sopimus-id
       :alkupvm alkupvm
       :loppupvm loppupvm
       :toimenpidekoodi toimenpidekoodi
       :tyyppi tyyppi
       :kohde kohde})))

(defn siirra-kanavatoimenpiteet [db user tiedot]
  (let [urakka-id (::toimenpide/urakka-id tiedot)
        siirto-kokonaishintaisiin? (= (::toimenpide/tyyppi tiedot) :kokonaishintainen)
        toimenpide-idt (::toimenpide/toimenpide-idt tiedot)]
    (assert urakka-id "Urakka-id puuttuu!")
    (if siirto-kokonaishintaisiin?
      (oikeudet/vaadi-oikeus "siirrä-kokonaishintaisiin" oikeudet/urakat-kanavat-lisatyot user urakka-id)
      (oikeudet/vaadi-oikeus "siirrä-muutos-ja-lisätöihin" oikeudet/urakat-kanavat-kokonaishintaiset user urakka-id))
    (q-toimenpide/vaadi-toimenpiteet-kuuluvat-urakkaan db (::toimenpide/toimenpide-idt tiedot) urakka-id)
    (jdbc/with-db-transaction
      [db db]
      ;; Tarkistetaan, että toimenpidekoodi, johon toimenpide liittyy, on olemassa myös uudella toimenpidetyypillä.
      ;; Mikäli näin ei ole, niin tehtäväksi vaihdetaan "Ei yksilöity".
      (let [tehtavat (q-toimenpide/hae-toimenpiteiden-tehtavan-hinnoittelu db toimenpide-idt)
            paivitettavat-tehtava-idt (into #{}
                                            (keep (fn [tehtava]
                                                    (::toimenpide/id
                                                      (if siirto-kokonaishintaisiin?
                                                        (when (tehtava-paivitetaan? "kokonaishintainen" tehtava) tehtava)
                                                        (when (tehtava-paivitetaan? "muutoshintainen" tehtava) tehtava)))))
                                            tehtavat)
            ei-yksiloity-tehtava-id (:id (first (q-toimenpidekoodit/hae-tehtavan-id
                                                  db
                                                  {:nimi "Ei yksilöity"
                                                   :kolmostason-tehtavan-koodi "24104"})))]
        (q-toimenpide/paivita-toimenpiteiden-tehtava db paivitettavat-tehtava-idt ei-yksiloity-tehtava-id)
        (q-toimenpide/paivita-toimenpiteiden-tyyppi db toimenpide-idt (::toimenpide/tyyppi tiedot))
        toimenpide-idt))))

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
      :siirra-kanavatoimenpiteet
      (fn [user hakuehdot]
        (siirra-kanavatoimenpiteet db user hakuehdot))
      {:kysely-spec ::toimenpide/siirra-kanavatoimenpiteet-kysely
       :vastaus-spec ::toimenpide/siirra-kanavatoimenpiteet-vastaus})
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
      :siirra-kanavatoimenpiteet
      :tallenna-kanavatoimenpide)
    this))