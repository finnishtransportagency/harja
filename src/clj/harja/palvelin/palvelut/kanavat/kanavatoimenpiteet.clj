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
            [harja.domain.vesivaylat.materiaali :as materiaali]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodit]
            [harja.kyselyt.vesivaylat.materiaalit :as q-materiaali]
            [harja.palvelin.palvelut.vesivaylat.materiaalit :as materiaali-palvelu]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.kommentti :as kommentti]
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodit]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as q-toimenpide]
            [clojure.java.jdbc :as jdbc]
            [harja.tyokalut.tietoturva :as tietoturva]))

(defn- vaadi-rivit-kuuluvat-emoon* [taulu rivit rivi-idt rivin-emo-id-avain vaadittu-emo-id]
  (let [emo-idt (set (keep rivin-emo-id-avain rivit))]
    (when (not= emo-idt #{vaadittu-emo-id})
      (throw (SecurityException. (str "Rivi-idt " rivi-idt " taulusta " taulu " eivät kuulu emoon " rivin-emo-id-avain ": " emo-idt " != " vaadittu-emo-id))))))


(defn vaadi-rivit-kuuluvat-emoon [db rivien-taulu rivin-emo-id-avain rivin-id-avain rivi-idt emo-id]
  (let [rivit (specql/fetch
                db
                rivien-taulu
                #{rivin-emo-id-avain rivin-id-avain}
                {rivin-id-avain (op/in rivi-idt)})]
    (when (not-empty rivi-idt)
      (vaadi-rivit-kuuluvat-emoon* rivien-taulu rivit rivi-idt rivin-emo-id-avain emo-id))))


(defn tallenna-kanavatoimenpiteen-hinnoittelu! [db user tiedot]
  (let [urakka-id (::toimenpide/urakka-id tiedot)
        toimenpide-id (::toimenpide/id tiedot)
        liita-tpid-mappeihin (fn [mapit k]
                               (mapv #(assoc % k toimenpide-id) mapit))]
    (log/debug "hinnoittelun tiedot: " (with-out-str
                                         (clojure.pprint/pprint tiedot)))
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
      (vaadi-rivit-kuuluvat-emoon db ::tyo/toimenpiteen-tyo ::tyo/toimenpide-id ::tyo/id olemassa-olevat-tyo-idt toimenpide-id)


      (jdbc/with-db-transaction [db db]
        (q-toimenpide/tallenna-toimenpiteen-omat-hinnat!
          {:db db
           :user user
           :hinnat (liita-tpid-mappeihin (::hinta/tallennettavat-hinnat tiedot) ::hinta/toimenpide-id)})
        (q-toimenpide/tallenna-toimenpiteen-tyot!
          {:db db
           :user user
           :tyot (liita-tpid-mappeihin (::tyo/tallennettavat-tyot tiedot) ::tyo/toimenpide-id)})
        (q-toimenpide/lisaa-kommentti! db user (if (and (empty? olemassa-olevat-tyo-idt)
                                                        (empty? olemassa-olevat-hinta-idt))
                                                 :luotu
                                                 :muokattu
                                                 ) "" toimenpide-id)
        (first (q-toimenpide/hae-kanavatoimenpiteet-specql db {::toimenpide/id toimenpide-id}))))))

(defn tallenna-kanavatoimenpiteen-hinnoittelun-kommentti! [db user tiedot]
  (let [urakka-id (::toimenpide/urakka-id tiedot)]
    (assert urakka-id "Urakka-id puuttuu!")

    (q-toimenpide/lisaa-kommentti! db user
                                   (::kommentti/tila tiedot)
                                   (::kommentti/kommentti tiedot)
                                   (::kommentti/toimenpide-id tiedot))

    (first (q-toimenpide/hae-kanavatoimenpiteet-specql db {::toimenpide/id (::kommentti/toimenpide-id tiedot)}))))

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
    (q-toimenpide/hae-kanavatomenpiteet-jeesql
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
                                                   :urakkaid urakka-id})))]
        (q-toimenpide/paivita-toimenpiteiden-tehtava db paivitettavat-tehtava-idt ei-yksiloity-tehtava-id)
        (q-toimenpide/paivita-toimenpiteiden-tyyppi db toimenpide-idt (::toimenpide/tyyppi tiedot))
        toimenpide-idt))))

(defn tallenna-materiaalikirjaukset [db fim email
                                     {kayttaja-id :id :as kayttaja}
                                     urakka-id
                                     {toimenpide-id ::toimenpide/id
                                      materiaalikirjaukset ::toimenpide/materiaalikirjaukset
                                      materiaalipoistot ::toimenpide/materiaalipoistot
                                      toimenpide-poistettu? ::muokkaustiedot/poistettu?
                                      :as toimenpide}]
  (assert (integer? urakka-id) "Materiaalikirjauksia ei voi tallentaa ilman urakka id:tä")
  (assert (integer? toimenpide-id) (str "Toimenpiteen materiaalikirjausta ei voi tallentaa ilman toimenpide-id:tä - " toimenpide-id))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-hairiotilanteet kayttaja urakka-id)
  (log/debug "tallenna-materiaalikirjaukset: kirjauksia/poistoja" (count materiaalikirjaukset) (count materiaalipoistot))
  (if toimenpide-poistettu?
    (q-materiaali/poista-toimenpiteen-kaikki-materiaalikirjaukset db kayttaja toimenpide-id)
    ;; else
    (do
      (doseq [kirjaus-ilman-tpid materiaalikirjaukset
              :let [kirjaus (assoc kirjaus-ilman-tpid ::materiaali/toimenpide toimenpide-id
                                                      ::materiaali/pvm (::toimenpide/pvm toimenpide))]]
        (q-materiaali/kirjaa-materiaali db kayttaja kirjaus)
        (materiaali-palvelu/hoida-halytysraja db kirjaus fim email))
      (let [poistettavien-materiaalien-idt (set (map ::materiaali/id materiaalipoistot))
            toimenpiteen-hinnat (q-toimenpide/hae-toimenpiteen-hinnat db toimenpide-id)
            poistettavat-hinnat (sequence (comp
                                            (filter #(and (poistettavien-materiaalien-idt (::hinta/materiaali-id %))
                                                          (= (::hinta/ryhma %) "materiaali")))
                                            (map #(assoc % ::muokkaustiedot/poistettu? true)))
                                          toimenpiteen-hinnat)]
        (when (not (empty? poistettavat-hinnat))
          (q-toimenpide/tallenna-toimenpiteen-omat-hinnat! {:db db
                                                            :user kayttaja
                                                            :hinnat poistettavat-hinnat})))
      (doseq [mk materiaalipoistot]
        (q-materiaali/poista-materiaalikirjaus db kayttaja (::materiaali/id mk))))))

(defn tallenna-kanavatoimenpide [db fim email user {tyyppi ::toimenpide/tyyppi
                                                    urakka-id ::toimenpide/urakka-id
                                                    :as toimenpide}]
  (tarkista-kutsu user urakka-id tyyppi)
  (log/debug "toimenpide-map: " (with-out-str
                                  (clojure.pprint/pprint toimenpide)))
  ;; Toimenpide kuuluu urakkaan
  (tietoturva/vaadi-linkitys db ::toimenpide/kanava-toimenpide ::toimenpide/id
                             (::toimenpide/id toimenpide) ::toimenpide/urakka-id urakka-id)
  ;; Kohde kuuluu urakkaan
  (when (::toimenpide/kohde-id toimenpide)
    (tietoturva/vaadi-ainakin-yksi-linkitys db ::kohde/kohde<->urakka ::kohde/kohde-id (::toimenpide/kohde-id toimenpide)
                                            ::kohde/urakka-id urakka-id))
  ;; Kohdeosa kuuluu kohteeseen
  (when (::toimenpide/kohteenosa-id toimenpide)
    (tietoturva/vaadi-linkitys db ::osa/kohteenosa ::osa/id (::toimenpide/kohteenosa-id toimenpide)
                               ::osa/kohde-id (::toimenpide/kohde-id toimenpide)))
  (jdbc/with-db-transaction [db db]
    (let [toimenpide-ilman-materiaaleja (dissoc toimenpide ::toimenpide/materiaalikirjaukset ::toimenpide/materiaalipoistot)
          tallennettu-toimenpide-map (q-toimenpide/tallenna-toimenpide db (:id user) toimenpide-ilman-materiaaleja)]
      ;; Jos kyseessä on muutos- ja lisätyön poisto, niin poistetaan myös sille merkatut hinnat
      (when (and (::muokkaustiedot/poistettu? toimenpide)
                 (= (::toimenpide/tyyppi toimenpide) :muutos-lisatyo))
        (q-toimenpide/tallenna-toimenpiteen-omat-hinnat! {:db db
                                                          :user user
                                                          :hinnat (map #(assoc % ::muokkaustiedot/poistettu? true)
                                                                       (q-toimenpide/hae-toimenpiteen-hinnat db (::toimenpide/id toimenpide)))})
        (q-toimenpide/tallenna-toimenpiteen-tyot! {:db db
                                                   :user user
                                                   :tyot (map #(assoc % ::muokkaustiedot/poistettu? true)
                                                              (q-toimenpide/hae-toimenpiteen-tyot db (::toimenpide/id toimenpide)))}))
      (tallenna-materiaalikirjaukset db fim email user urakka-id (merge toimenpide tallennettu-toimenpide-map)))))

(defrecord Kanavatoimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           fim :fim
           email :sonja-sahkoposti
           :as this}]
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
      :tallenna-kanavatoimenpiteen-hinnoittelun-kommentti
      (fn [user hakuehdot]
        (tallenna-kanavatoimenpiteen-hinnoittelun-kommentti! db user hakuehdot))
      {:kysely-spec ::toimenpide/tallenna-kanavatoimenpiteen-hinnoittelun-kommentti-kysely
       :vastaus-spec ::toimenpide/tallenna-kanavatoimenpiteen-hinnoittelun-kommentti-vastaus})
    (julkaise-palvelu
      http
      :tallenna-kanavatoimenpide
      (fn [user {toimenpide ::toimenpide/tallennettava-kanava-toimenpide
                 hakuehdot ::toimenpide/hae-kanavatoimenpiteet-kysely}]
        (tallenna-kanavatoimenpide db fim email user toimenpide)
        {:kanavatoimenpiteet (hae-kanavatoimenpiteet db user hakuehdot)
         :materiaalilistaus (q-materiaali/hae-materiaalilistaus db {::materiaali/urakka-id (::toimenpide/urakka-id toimenpide)})})
      {:kysely-spec ::toimenpide/tallenna-kanavatoimenpide-kutsu
       :vastaus-spec ::toimenpide/tallenna-kanavatoimenpide-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kanavatoimenpiteet
      :tallenna-kanavatoimenpiteen-hinnoittelu
      :siirra-kanavatoimenpiteet
      :tallenna-kanavatoimenpide)
    this))
