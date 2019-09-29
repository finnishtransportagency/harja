(ns harja.palvelin.palvelut.budjettisuunnittelu
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.string :as clj-str]
            [specql.core :refer [fetch update! insert!]]
            [specql.op :as op]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.pvm :as pvm]
            [harja.kyselyt
             [budjettisuunnittelu :as q]
             [urakat :as urakat-q]
             [sopimukset :as sopimus-q]
             [kokonaishintaiset-tyot :as kok-q]
             [toimenpidekoodit :as tpik-q]
             [toimenpideinstanssit :as tpi-q]]
            [harja.palvelin.palvelut
             [kokonaishintaiset-tyot :as sampo-kustannussuunnitelmat]
             [kiinteahintaiset-tyot :as kiinthint-tyot]
             [kustannusarvioidut-tyot :as kustarv-tyot]
             [yksikkohintaiset-tyot :as ykshint-tyot]]
            [harja.domain
             [oikeudet :as oikeudet]
             [budjettisuunnittelu :as bs]
             [toimenpideinstanssi :as tpi]
             [roolit :as roolit]]))

(defn hae-urakan-tavoite
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (q/hae-budjettitavoite db {:urakka urakka-id}))

(defn tallenna-urakan-tavoite
  "Palvelu joka tallentaa urakan budjettiin liittyvät tavoitteet: tavoitehinta, kattohinta ja edelliseltä hoitovuodelta siirretty tavoitehinnan lisä/vähennys.
  Budjettitiedoissa: hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta.
  Budjettitavoitteet-vektorissa voi lähettää yhden tai useamman mäpin, jossa kussakin urakan yhden hoitokauden tiedot."
  [db user {:keys [urakka-id tavoitteet]}]

  (let [urakkatyyppi (keyword (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä " urakkatyyppi ". Tavoite kirjataan vain teiden hoidon urakoille."))))
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id))

  (assert (vector? tavoitteet) "tavoitteet tulee olla vektori")

  (jdbc/with-db-transaction [c db]
                            (let [tavoitteet-kannassa (q/hae-budjettitavoite c {:urakka urakka-id})
                                  tallennettavat-hoitokaudet (into #{} (map :hoitokausi tavoitteet))
                                  paivitettavat-tavoitteet (into #{}
                                                                 (map :hoitokausi)
                                                                 (filter #(tallennettavat-hoitokaudet (:hoitokausi %)) tavoitteet-kannassa))]
                              (doseq [hoitokausitavoite tavoitteet]
                                (as-> hoitokausitavoite hkt
                                      (assoc hkt :urakka urakka-id)
                                      (assoc hkt :kayttaja (:id user))
                                      (if (not (paivitettavat-tavoitteet (:hoitokausi hkt)))
                                        (q/tallenna-budjettitavoite<! c hkt)
                                        (q/paivita-budjettitavoite<! c hkt)))))))

(defn hae-urakan-budjetoidut-tyot
  "Palvelu, joka palauttaa urakan budjetoidut työt. Palvelu palauttaa kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset työt mapissa jäsenneltynä."
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  {:kiinteahintaiset-tyot (kiinthint-tyot/hae-urakan-kiinteahintaiset-tyot db user urakka-id)
   :kustannusarvioidut-tyot (kustarv-tyot/hae-urakan-kustannusarvioidut-tyot db user urakka-id)
   :yksikkohintaiset-tyot (ykshint-tyot/hae-urakan-yksikkohintaiset-tyot db user urakka-id)
   :johto-ja-hallintokorvaukset (map (fn [jhk]
                                       (into {}
                                             (map (fn [[k v]]
                                                    [(keyword (clj-str/replace (name k) #"_" "-")) v])
                                                  (update jhk ::bs/toimenkuva (fn [{::bs/keys [toimenkuva]}]
                                                                                toimenkuva)))))
                                     (fetch db ::bs/johto-ja-hallintokorvaus
                                            #{::bs/tunnit ::bs/tuntipalkka ::bs/kk-v ::bs/maksukausi
                                              ::bs/hoitokausi
                                              [::bs/toimenkuva #{::bs/toimenkuva}]}
                                            {::bs/urakka-id urakka-id}))})

(defn tallenna-budjetoidut-tyot
  "Palvelu joka tallentaa urakan kustannusarvioidut tyot."
  [db user {:keys [urakka-id tyot]}]

  (let [urakkatyyppi (keyword (:tyyppi
                                (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
        sopimusnumero (:id
                        (first (sopimus-q/hae-urakan-paasopimus db urakka-id))) ;; teiden hoidon urakoissa (MHU) on vain yksi sopimus
        tallennettavat-toimenpideinstanssit (into #{}
                                                  (keep :toimenpideinstanssi (concat (:kiinteahintaiset-tyot tyot)
                                                                                     (:kustannusarvioidut-tyot tyot)
                                                                                     (:yksikkohintaiset-tyot tyot))))
        tallennettavat-toimenpideinstanssit-urakassa (into #{}
                                                           (map :id)
                                                           (tpi-q/urakan-toimenpideinstanssit-idlla db urakka-id tallennettavat-toimenpideinstanssit))]

    ;; Tarkistetaan oikeudet ja että kyseessä on maanteiden hoidon urakka (MHU) ja että käsitellyt toimenpideinstanssit kuuluvat urakkaan.
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakan " urakka-id " budjetoituja töitä ei voi tallentaa urakkatyypillä " urakkatyyppi "."))))
    (when-not (empty?
                (set/difference tallennettavat-toimenpideinstanssit tallennettavat-toimenpideinstanssit-urakassa))
      (throw (IllegalArgumentException. "Väärän urakan toimenpideinstanssi")))

    (jdbc/with-db-transaction [c db]
                              (kiinthint-tyot/tallenna-kiinteahintaiset-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:kiinteahintaiset-tyot tyot)})
                              (kustarv-tyot/tallenna-kustannusarvioidut-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:kustannusarvioidut-tyot tyot)})
                              (ykshint-tyot/tallenna-urakan-yksikkohintaiset-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:yksikkohintaiset-tyot tyot)})

                              ;; Merkitään likaiseksi tallennettujen toimenpideinstanssien kustannussuunnitelmat.
                              ;; Periaatteessa tässä voisi myös selvittää ovatko kaikki tiedot päivittyneet ja jättää tarvittaessa osa kustannussuunnitelmista päivittämättä.
                              (when not-empty tallennettavat-toimenpideinstanssit-urakassa
                                              (kok-q/merkitse-kustannussuunnitelmat-likaisiksi! c tallennettavat-toimenpideinstanssit-urakassa))

                              ;; Palautetaan päivitetty tilanne
                              (hae-urakan-budjetoidut-tyot c user {:urakka-id urakka-id}))))

(defn tallenna-yksikkohintainen-tyo
  [db user {:keys [urakka-id tehtava tyot]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [{tehtava-id :id} (first (tpik-q/hae-tehtavan-id db {:nimi tehtava
                                                                                      :urakkaid urakka-id}))
                                  olemassa-olevat-tyot (fetch db ::bs/yksikkohintainen-tyo
                                                              #{::bs/id ::bs/vuosi}
                                                              {::bs/urakka urakka-id
                                                               ::bs/vuosi (op/in (into #{} (map :vuosi tyot)))
                                                               ::bs/tehtava tehtava-id})
                                  paivitetaan? (not (empty? olemassa-olevat-tyot))]
                              (if paivitetaan?
                                (doseq [{:keys [yksikkohinta vuosi]} tyot
                                        olemassa-oleva-tyo (filter #(= vuosi (::bs/vuosi %)) olemassa-olevat-tyot)]
                                  (update! db ::bs/yksikkohintainen-tyo
                                           {::bs/yksikkohinta yksikkohinta}
                                           {::bs/id (::bs/id olemassa-oleva-tyo)}))
                                (let [{:keys [alkupvm loppupvm]} (first (urakat-q/hae-urakka db urakka-id))
                                      paasopimus (urakat-q/urakan-paasopimus-id db urakka-id)
                                      alkuvuosi (pvm/vuosi alkupvm)
                                      loppuvuosi (pvm/vuosi loppupvm)]
                                  (doseq [{:keys [yksikkohinta vuosi]} tyot
                                          :let [kuukaudet (cond
                                                            (= alkuvuosi vuosi) [10 12]
                                                            (= loppuvuosi vuosi) [1 9]
                                                            :else [1 12])]
                                          kk (range (first kuukaudet) (inc (second kuukaudet)))]
                                    (insert! db ::bs/yksikkohintainen-tyo
                                             {::bs/maara 1
                                              ::bs/yksikko "kk"
                                              ::bs/yksikkohinta yksikkohinta
                                              ::bs/urakka urakka-id
                                              ::bs/sopimus paasopimus
                                              ::bs/tehtava tehtava-id
                                              ::bs/kuukausi kk
                                              ::bs/vuosi vuosi
                                              ::bs/luoja (:id user)
                                              ::bs/luotu (pvm/nyt)}))))
                              {:onnistui? true})))

(defn tallenna-johto-ja-hallintokorvaukset
  [db user {:keys [urakka-id toimenkuva maksukausi jhkt]}]
  ;"urakka-id", "toimenkuva-id", maksukausi, hoitokausi, tunnit, tuntipalkka
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [toimenkuva-id (first (fetch db ::bs/johto-ja-hallintokorvaus-toimenkuva
                                                              #{::bs/id ::bs/toimenkuva}
                                                              {::bs/toimenkuva toimenkuva}))
                                  olemassa-olevat-jhkt (fetch db ::bs/johto-ja-hallintokorvaus
                                                              #{::bs/id ::bs/toimenkuva-id ::bs/maksukausi ::bs/hoitokausi}
                                                              {::bs/urakka-id urakka-id
                                                               ::bs/hoitokausi (op/in (into #{} (map :hoitokausi jhkt)))
                                                               ::bs/toimenkuva-id toimenkuva-id
                                                               ::bs/maksukausi maksukausi})
                                  paivitetaan? (not (empty? olemassa-olevat-jhkt))]
                              (if paivitetaan?
                                (doseq [{:keys [hoitokausi tunnit tuntipalkka]} jhkt]
                                  (let [id (some #(when (and (= hoitokausi (::bs/hoitokausi %))
                                                             (= toimenkuva-id (::bs/toimenkuva-id %))
                                                             (= maksukausi (::bs/maksukausi %)))
                                                    (::bs/id %))
                                                 olemassa-olevat-jhkt)]
                                    (update! db ::bs/johto-ja-hallintokorvaus
                                             {::bs/tunnit tunnit
                                              ::bs/tuntipalkka tuntipalkka}
                                             {::bs/id id})))
                                (doseq [{:keys [hoitokausi tunnit tuntipalkka kk-v]} jhkt]
                                  (insert! db ::bs/johto-ja-hallintokorvaus
                                           {::bs/urakka-id urakka-id
                                            ::bs/toimenkuva-id toimenkuva-id
                                            ::bs/tunnit tunnit
                                            ::bs/tuntipalkka tuntipalkka
                                            ::bs/kk-v kk-v
                                            ::bs/maksukausi maksukausi
                                            ::bs/hoitokausi hoitokausi
                                            ::bs/luotu (pvm/nyt)
                                            ::bs/luoja (:id user)})))
                              {:onnistui? true})))

(s/def ::vuosi integer?)
(s/def ::yksikkohinta integer?)
(s/def ::urakka-id integer?)
(s/def ::tehtava string?)
(s/def ::toimenkuva string?)
(s/def ::maksukausi keyword?)
(s/def ::hoitokausi integer?)
(s/def ::tunnit number?)
(s/def ::tuntipalkka number?)
(s/def ::kk-v number?)

(s/def ::yksikkohintainen-tyo (s/keys :req-un [::vuosi ::yksikkohinta]))
(s/def ::tyot (s/coll-of ::yksikkohintainen-tyo))

(s/def ::jhk (s/keys :req-un [::hoitokausi ::tunnit ::tuntipalkka ::kk-v]))
(s/def ::jhkt (s/coll-of ::jhk))

(s/def ::tallenna-yksikkohintainen-tyo-kysely (s/keys :req-un [::urakka-id ::tehtava ::tyot]))
(s/def ::tallenna-yksikkohintainen-tyo-vastaus any?)

(s/def ::tallenna-johto-ja-hallintokorvaukset-kysely (s/keys :req-un [::urakka-id ::toimenkuva ::maksukausi ::jhkt]))
(s/def ::tallenna-johto-ja-hallintokorvaukset-vastaus any?)

(defrecord Budjettisuunnittelu []
  component/Lifecycle
  (start [this]
    (let [{:keys [db]} this]
      (when (ominaisuus-kaytossa? :mhu-urakka)
        (doto (:http-palvelin this)
          (julkaise-palvelu
            :budjetoidut-tyot (fn [user tiedot]
                                (hae-urakan-budjetoidut-tyot db user tiedot)))
          (julkaise-palvelu
            :tallenna-budjetoidut-tyot (fn [user tiedot]
                                         (tallenna-budjetoidut-tyot db user tiedot)))
          (julkaise-palvelu
            :budjettitavoite (fn [user tiedot]
                               (hae-urakan-tavoite db user tiedot)))
          (julkaise-palvelu
            :tallenna-budjettitavoite (fn [user tiedot]
                                        (tallenna-urakan-tavoite db user tiedot)))
          (julkaise-palvelu
            :tallenna-yksikkohintainen-tyo
            (fn [user tiedot]
              (tallenna-yksikkohintainen-tyo db user tiedot))
            {:kysely-spec ::tallenna-yksikkohintainen-tyo-kysely
             :vastaus-spec ::tallenna-yksikkohintainen-tyo-vastaus})
          (julkaise-palvelu
            :tallenna-johto-ja-hallintokorvaukset
            (fn [user tiedot]
              (tallenna-johto-ja-hallintokorvaukset db user tiedot))
            {:kysely-spec ::tallenna-johto-ja-hallintokorvaukset-kysely
             :vastaus-spec ::tallenna-johto-ja-hallintokorvaukset-vastaus}))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :budjettitavoite)
    (poista-palvelu (:http-palvelin this) :tallenna-budjettitavoite)
    this))
