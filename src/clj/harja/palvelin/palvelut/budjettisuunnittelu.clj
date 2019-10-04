(ns harja.palvelin.palvelut.budjettisuunnittelu
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.string :as clj-str]
            [specql.core :refer [fetch update! insert!]]
            [specql.op :as op]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.pvm :as pvm]
            [harja.kyselyt
             [budjettisuunnittelu :as q]
             [urakat :as urakat-q]
             [toimenpideinstanssit :as tpi-q]]
            [harja.palvelin.palvelut
             [kiinteahintaiset-tyot :as kiinthint-tyot]
             [kustannusarvioidut-tyot :as kustarv-tyot]]
            [harja.domain
             [oikeudet :as oikeudet]
             [budjettisuunnittelu :as bs]
             [toimenpidekoodi :as tpk]
             [tehtavaryhma :as tr]]))

(def tallennettava-asia #{:hoidonjohtopalkkio
                          :toimistokulut
                          :erillishankinnat
                          :rahavaraus-lupaukseen-1
                          :kolmansien-osapuolten-aiheuttamat-vahingot
                          :akilliset-hoitotyot
                          :toimenpiteen-maaramitattavat-tyot})

(defn- key-from-val [m v]
  (some (fn [[k v_]]
          (when (= v v_)
            k))
        m))

(def ^{:private true} toimenpide-avain->toimenpide
  {:paallystepaikkaukset "20107"
   :mhu-yllapito "20191"
   :talvihoito "23104"
   :liikenneympariston-hoito "23116"
   :sorateiden-hoito "23124"
   :mhu-korvausinvestointi "14301"
   :mhu-johto "23151"})

(defn- toimenpide->toimenpide-avain [v]
  (key-from-val toimenpide-avain->toimenpide v))

(def ^{:private true} tallennettava-asia->tyyppi
  {:hoidonjohtopalkkio "laskutettava-tyo"
   :toimistokulut "laskutettava-tyo"
   :erillishankinnat "laskutettava-tyo"
   :rahavaraus-lupaukseen-1 "muut-rahavaraukset"
   :kolmansien-osapuolten-aiheuttamat-vahingot "vahinkojen-korjaukset"
   :akilliset-hoitotyot "akillinen-hoitotyo"
   :toimenpiteen-maaramitattavat-tyot "laskutettava-tyo"})

(defn- tyyppi->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tyyppi v))

(def ^{:private true} tallennettava-asia->tehtava
  {:hoidonjohtopalkkio "Hoitourakan työnjohto"
   :toimistokulut "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
   :kolmansien-osapuolten-aiheuttamat-vahingot "Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen"
   :akilliset-hoitotyot "Äkillinen hoitotyö"})

(defn- tehtava->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtava v))

(def ^{:private true} tallennettava-asia->tehtavaryhma
  {:erillishankinnat "ERILLISHANKINNAT"
   :rahavaraus-lupaukseen-1 "TILAAJAN RAHAVARAUS"})

(defn- tehtavaryhma->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtavaryhma v))

(defn hae-urakan-tavoite
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (q/hae-budjettitavoite db {:urakka urakka-id}))

(defn tallenna-urakan-tavoite
  "Palvelu joka tallentaa urakan budjettiin liittyvät tavoitteet: tavoitehinta, kattohinta ja edelliseltä hoitovuodelta siirretty tavoitehinnan lisä/vähennys.
  Budjettitiedoissa: hoitokausi, tavoitehinta, kattohinta.
  Budjettitavoitteet-vektorissa voi lähettää yhden tai useamman mäpin, jossa kussakin urakan yhden hoitokauden tiedot."
  [db user {:keys [urakka-id tavoitteet]}]

  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))]
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

(defn hae-urakan-kustannusarvoidut-tyot
  [db user urakka-id]
  (let [kustannusarvoidut-tyot (kustarv-tyot/hae-urakan-kustannusarvoidut-tyot-nimineen db user urakka-id)]
    (map (fn [tyo]
           (-> tyo
               (assoc :toimenpide-avain (toimenpide->toimenpide-avain (:toimenpiteen-koodi tyo)))
               (assoc :haettu-asia (or (tehtava->tallennettava-asia (:tehtava-nimi tyo))
                                       (tehtavaryhma->tallennettava-asia (:tehtavaryhman-nimi tyo))))
               (dissoc :toimenpiteen-koodi :tehtavan-nimi :tehtavaryhman-nimi)))
         kustannusarvoidut-tyot)))

(defn hae-urakan-budjetoidut-tyot
  "Palvelu, joka palauttaa urakan budjetoidut työt. Palvelu palauttaa kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset työt mapissa jäsenneltynä."
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  {:kiinteahintaiset-tyot (kiinthint-tyot/hae-urakan-kiinteahintaiset-tyot db user urakka-id)
   :kustannusarvioidut-tyot  (hae-urakan-kustannusarvoidut-tyot db user urakka-id)
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

(defn- mudosta-ajat [ajat urakan-alkuvuosi urakan-loppuvuosi]
  (reduce (fn [ajat {:keys [vuosi kuukausi]}]
            (if kuukausi
              (conj ajat {:vuosi vuosi
                          :kuukausi kuukausi})
              (let [kuukaudet (cond
                                (= urakan-alkuvuosi vuosi) [10 12]
                                (= urakan-loppuvuosi vuosi) [1 9]
                                :else [1 12])]
                (into []
                      (concat ajat
                              (map (fn [kk]
                                     {:vuosi vuosi
                                      :kuukausi kk})
                                   (range (first kuukaudet) (inc (second kuukaudet)))))))))
          [] ajat))

(defn tallenna-kiinteahintaiset-tyot
  [db user {:keys [urakka-id toimenpide-avain ajat summa]}]
  {:pre [(integer? urakka-id)
         (keyword? toimenpide-avain)
         (number? summa)]}
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [toimenpide (toimenpide-avain->toimenpide toimenpide-avain)
          {toimenpide-id ::tpk/id} (first (fetch db ::tpk/toimenpidekoodi
                                                 #{::tpk/id}
                                                 {::tpk/taso 3
                                                  ::tpk/koodi toimenpide}))
          {toimenpideinstanssi-id :id} (first (tpi-q/hae-urakan-toimenpideinstanssi db {:urakka urakka-id :tp toimenpide-id}))
          _ (when (nil? toimenpideinstanssi-id)
              (throw (Exception. "Toimenpideinstanssia ei löydetty")))

          {:keys [alkupvm loppupvm]} (first (urakat-q/hae-urakka db urakka-id))
          alkuvuosi (pvm/vuosi alkupvm)
          loppuvuosi (pvm/vuosi loppupvm)
          ajat (mudosta-ajat ajat alkuvuosi loppuvuosi)

          olemassa-olevat-kiinteahintaiset-tyot-vuosille (fetch db ::bs/kiinteahintainen-tyo
                                                                #{::bs/id ::bs/smallint-v ::bs/smallint-kk}
                                                                {::bs/smallint-v (op/in (into #{} (distinct (map :vuosi ajat))))
                                                                 ::bs/toimenpideinstanssi toimenpideinstanssi-id})
          olemassa-olevat-kiinteahintaiset-tyot (filter (fn [{::bs/keys [smallint-v smallint-kk]}]
                                                          (some #(and (= (:vuosi %) smallint-v)
                                                                      (= (:kuukausi %) smallint-kk))
                                                                ajat))
                                                        olemassa-olevat-kiinteahintaiset-tyot-vuosille)
          uudet-kiinteahintaiset-tyot-ajat (remove (fn [{:keys [vuosi kuukausi]}]
                                                     (some #(and (= vuosi (::bs/smallint-v %))
                                                                 (= kuukausi (::bs/smallint-kk %)))
                                                           olemassa-olevat-kiinteahintaiset-tyot))
                                                   ajat)]
      (when-not (empty? olemassa-olevat-kiinteahintaiset-tyot)
        (doseq [olemassa-oleva-tyo olemassa-olevat-kiinteahintaiset-tyot]
          (update! db ::bs/kiinteahintainen-tyo
                   {::bs/summa summa}
                   {::bs/id (::bs/id olemassa-oleva-tyo)})))
      (when-not (empty? uudet-kiinteahintaiset-tyot-ajat)
        (let [paasopimus (urakat-q/urakan-paasopimus-id db urakka-id)]
          (doseq [{:keys [vuosi kuukausi]} uudet-kiinteahintaiset-tyot-ajat]
            (insert! db ::bs/kiinteahintainen-tyo
                     {::bs/smallint-v vuosi
                      ::bs/smallint-kk kuukausi
                      ::bs/summa summa
                      ::bs/toimenpideinstanssi toimenpideinstanssi-id
                      ::bs/sopimus paasopimus
                      ::bs/luotu (pvm/nyt)
                      ::bs/luoja (:id user)}))))
      {:onnistui? true})))

(defn tallenna-johto-ja-hallintokorvaukset
  [db user {:keys [urakka-id toimenkuva maksukausi jhkt]}]
  {:pre [(integer? urakka-id)
         (string? toimenkuva)
         (keyword? maksukausi)]}
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [toimenkuva-id (::bs/id (first (fetch db ::bs/johto-ja-hallintokorvaus-toimenkuva
                                                                       #{::bs/id}
                                                                       {::bs/toimenkuva toimenkuva})))
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

(defn tallenna-kustannusarvioitu-tyo!
  [db user {:keys [tyyppi tehtava tehtavaryhma toimenpide urakka-id ajat summa]}]
  {:pre [(string? tyyppi)
         (string? toimenpide)
         (integer? urakka-id)
         (sequential? ajat)]}
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [{tehtava-id ::tpk/id} (when tehtava
                                  (first (fetch db ::tpk/toimenpidekoodi
                                                #{::tpk/id
                                                  ;; Tämä join pitää hakea, jotta tuo where claussin join toimii
                                                  [::tpk/toimenpidekoodi-join #{::tpk/nimi}]}
                                                {::tpk/nimi tehtava
                                                 ::tpk/toimenpidekoodi-join {::tpk/koodi toimenpide}})))
          {toimenpide-id ::tpk/id} (first (fetch db ::tpk/toimenpidekoodi
                                                 #{::tpk/id}
                                                 {::tpk/taso 3
                                                  ::tpk/koodi toimenpide}))
          {tehtavaryhma-id ::tr/id} (when tehtavaryhma
                                      (first (fetch db ::tr/tehtavaryhma
                                                    #{::tr/id}
                                                    {::tr/nimi tehtavaryhma})))
          {toimenpideinstanssi-id :id} (first (tpi-q/hae-urakan-toimenpideinstanssi db {:urakka urakka-id :tp toimenpide-id}))
          _ (when (nil? toimenpideinstanssi-id)
              (throw (Exception. "Toimenpideinstanssia ei löydetty")))
          tyyppi (keyword tyyppi)


          {:keys [alkupvm loppupvm]} (first (urakat-q/hae-urakka db urakka-id))
          alkuvuosi (pvm/vuosi alkupvm)
          loppuvuosi (pvm/vuosi loppupvm)
          ajat (mudosta-ajat ajat alkuvuosi loppuvuosi)
          kustannusarvioitu-tyo-params (into {}
                                             (map (fn [[k v]]
                                                     (if (nil? v)
                                                       [k op/null?]
                                                       [k v]))
                                                   {::bs/smallint-v (op/in (into #{} (distinct (map :vuosi ajat))))
                                                    ::bs/tehtava tehtava-id
                                                    ::bs/tehtavaryhma tehtavaryhma-id
                                                    ::bs/tyyppi tyyppi
                                                    ::bs/toimenpideinstanssi toimenpideinstanssi-id}))

          olemassa-olevat-kustannusarvioidut-tyot-vuosille (fetch db ::bs/kustannusarvioitu-tyo
                                                                  #{::bs/id ::bs/smallint-v ::bs/smallint-kk}
                                                                  kustannusarvioitu-tyo-params)
          olemassa-olevat-kustannusarvioidut-tyot (filter (fn [{::bs/keys [smallint-v smallint-kk]}]
                                                            (some #(and (= (:vuosi %) smallint-v)
                                                                        (= (:kuukausi %) smallint-kk))
                                                                  ajat))
                                                          olemassa-olevat-kustannusarvioidut-tyot-vuosille)
          paivitetaan? (not (empty? olemassa-olevat-kustannusarvioidut-tyot))]
      (if paivitetaan?
        (doseq [olemassa-oleva-tyo olemassa-olevat-kustannusarvioidut-tyot]
          (update! db ::bs/kustannusarvioitu-tyo
                   {::bs/summa summa}
                   {::bs/id (::bs/id olemassa-oleva-tyo)}))
        (let [paasopimus (urakat-q/urakan-paasopimus-id db urakka-id)]
          (doseq [{:keys [vuosi kuukausi]} ajat]
            (insert! db ::bs/kustannusarvioitu-tyo
                     {::bs/smallint-v vuosi
                      ::bs/smallint-kk kuukausi
                      ::bs/summa summa
                      ::bs/tyyppi tyyppi
                      ::bs/tehtava tehtava-id
                      ::bs/tehtavaryhma tehtavaryhma-id
                      ::bs/toimenpideinstanssi toimenpideinstanssi-id
                      ::bs/sopimus paasopimus
                      ::bs/luotu (pvm/nyt)
                      ::bs/luoja (:id user)}))))
      {:onnistui? true})))


(defn tallenna-kustannusarvioitu-tyo
  [db user {:keys [urakka-id tallennettava-asia toimenpide-avain summa ajat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [tyyppi (tallennettava-asia->tyyppi tallennettava-asia)
          tehtava (tallennettava-asia->tehtava tallennettava-asia)
          tehtavaryhma (tallennettava-asia->tehtavaryhma tallennettava-asia)
          toimenpide (toimenpide-avain->toimenpide toimenpide-avain)]
      (tallenna-kustannusarvioitu-tyo! db user {:tyyppi tyyppi
                                                :tehtava tehtava
                                                :tehtavaryhma tehtavaryhma
                                                :toimenpide toimenpide
                                                :urakka-id urakka-id
                                                :ajat ajat
                                                :summa summa}))))


(s/def ::vuosi integer?)
(s/def ::kuukausi integer?)
(s/def ::urakka-id integer?)
(s/def ::toimenkuva string?)
(s/def ::maksukausi keyword?)
(s/def ::hoitokausi integer?)
(s/def ::tunnit number?)
(s/def ::tuntipalkka number?)
(s/def ::kk-v number?)
(s/def ::summa number?)
(s/def ::tavoitehinta number?)
(s/def ::kattohinta number?)
(s/def ::toimenpide-avain (s/and keyword?
                                 (fn [k]
                                   (some #(= k %)
                                         (keys toimenpide-avain->toimenpide)))))
(s/def ::tallennettava-asia tallennettava-asia)

(s/def ::jhk (s/keys :req-un [::hoitokausi ::tunnit ::tuntipalkka ::kk-v]))
(s/def ::jhkt (s/coll-of ::jhk))

(s/def ::ajat (s/coll-of (s/keys :req-un [::vuosi]
                                 :opt-un [::kuukausi])))

(s/def ::tavoitteet (s/coll-of (s/keys :req-un [::hoitokausi ::tavoitehinta ::kattohinta])
                               :kind vector?))

(s/def ::tallenna-johto-ja-hallintokorvaukset-kysely (s/keys :req-un [::urakka-id ::toimenkuva ::maksukausi ::jhkt]))
(s/def ::tallenna-johto-ja-hallintokorvaukset-vastaus any?)

(s/def ::tallenna-kustannusarvioitu-tyo-kysely (s/keys :req-un [::urakka-id ::tallennettava-asia ::toimenpide-avain ::summa ::ajat]))
(s/def ::tallenna-kustannusarvioitu-tyo-vastaus any?)

(s/def ::tallenna-kiinteahintaiset-tyot-kysely (s/keys :req-un [::urakka-id ::toimenpide-avain ::ajat ::summa]))
(s/def ::tallenna-kiinteahintaiset-tyot-vastaus any?)

(s/def ::tallenna-budjettitavoite-kysely (s/keys :req-un [::urakka-id ::tavoitteet]))
(s/def ::tallenna-budjettitavoite-vastaus any?)

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
            :budjettitavoite (fn [user tiedot]
                               (hae-urakan-tavoite db user tiedot)))
          (julkaise-palvelu
            :tallenna-budjettitavoite (fn [user tiedot]
                                        (tallenna-urakan-tavoite db user tiedot))
            {:kysely-spec ::tallenna-budjettitavoite-kysely
             :vastaus-spec ::tallenna-budjettitavoite-vastaus})

          (julkaise-palvelu
            :tallenna-kiinteahintaiset-tyot
            (fn [user tiedot]
              (tallenna-kiinteahintaiset-tyot db user tiedot))
            {:kysely-spec ::tallenna-kiinteahintaiset-tyot-kysely
             :vastaus-spec ::tallenna-kiinteahintaiset-tyot-vastaus})
          (julkaise-palvelu
            :tallenna-johto-ja-hallintokorvaukset
            (fn [user tiedot]
              (tallenna-johto-ja-hallintokorvaukset db user tiedot))
            {:kysely-spec ::tallenna-johto-ja-hallintokorvaukset-kysely
             :vastaus-spec ::tallenna-johto-ja-hallintokorvaukset-vastaus})
          (julkaise-palvelu
            :tallenna-kustannusarvioitu-tyo
            (fn [user tiedot]
              (tallenna-kustannusarvioitu-tyo db user tiedot))
            {:kysely-spec ::tallenna-kustannusarvioitu-tyo-kysely
             :vastaus-spec ::tallenna-kustannusarvioitu-tyo-vastaus}))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :budjetoidut-tyot
                     :budjettitavoite
                     :tallenna-budjettitavoite
                     :tallenna-kiinteahintaiset-tyot
                     :tallenna-johto-ja-hallintokorvaukset
                     :tallenna-kustannusarvioitu-tyo)
    this))
