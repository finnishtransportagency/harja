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
             [toimenpidekoodi :as tpk]
             [tehtavaryhma :as tr]
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

(def toimenpide-avaimet [:paallystepaikkaukset :mhu-yllapito :talvihoito :liikenneympariston-hoito :sorateiden-hoito :mhu-korvausinvestointi :mhu-johto])
(def tallennettava-asia #{:hoidonjohtopalkkio
                          :toimistokulut
                          :erillishankinnat
                          :rahavaraus-lupaukseen-1
                          :kolmansien-osapuolten-aiheuttamat-vahingot
                          :akilliset-hoitotyot
                          :toimenpiteen-maaramitattavat-tyot})
(def toimenpiteet (zipmap toimenpide-avaimet
                          ["Päällysteiden paikkaus (hoidon ylläpito)"
                           "MHU Ylläpito"
                           "Talvihoito laaja TPI"
                           "Liikenneympäristön hoito laaja TPI"
                           "Soratien hoito laaja TPI"
                           "MHU Korvausinvestointi"
                           "MHU ja HJU Hoidon johto"]))

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
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [toimenpide (get toimenpiteet
                          toimenpide-avain)
          {toimenpide-id ::tpk/id} (first (fetch db ::tpk/toimenpidekoodi
                                                 #{::tpk/id}
                                                 {::tpk/taso 3
                                                  ::tpk/nimi toimenpide}))
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
  ;"urakka-id", "toimenkuva-id", maksukausi, hoitokausi, tunnit, tuntipalkka
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
                                                 ::tpk/toimenpidekoodi-join {::tpk/nimi toimenpide}})))
          {toimenpide-id ::tpk/id} (first (fetch db ::tpk/toimenpidekoodi
                                                 #{::tpk/id}
                                                 {::tpk/taso 3
                                                  ::tpk/nimi toimenpide}))
          {tehtavaryhma-id ::tr/id} (when tehtavaryhma
                                      (fetch db ::tr/tehtavaryhma
                                             #{::tr/id}
                                             {::tr/nimi tehtava}))
          {toimenpideinstanssi-id :id} (first (tpi-q/hae-urakan-toimenpideinstanssi db {:urakka urakka-id :tp toimenpide-id}))
          _ (when (nil? toimenpideinstanssi-id)
              (throw (Exception. "Toimenpideinstanssia ei löydetty")))
          tyyppi (keyword tyyppi)


          {:keys [alkupvm loppupvm]} (first (urakat-q/hae-urakka db urakka-id))
          alkuvuosi (pvm/vuosi alkupvm)
          loppuvuosi (pvm/vuosi loppupvm)
          ajat (mudosta-ajat ajat alkuvuosi loppuvuosi)

          olemassa-olevat-kustannusarvioidut-tyot-vuosille (fetch db ::bs/kustannusarvioitu-tyo
                                                                  #{::bs/id ::bs/smallint-v ::bs/smallint-kk}
                                                                  {::bs/smallint-v (op/in (into #{} (distinct (map :vuosi ajat))))
                                                                   ::bs/tehtava tehtava-id
                                                                   ::bs/tehtavaryhma tehtavaryhma-id
                                                                   ::bs/tyyppi tyyppi
                                                                   ::bs/toimenpideinstanssi toimenpideinstanssi-id})
          olemassa-olevat-kustannusarvioidut-tyot (filter (fn [{::bs/keys [smallint-v slmallint-kk]}]
                                                            (some #(and (= (:vuosi %) smallint-v)
                                                                        (= (:kuukausi %) slmallint-kk))
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
    (let [tyyppi (case tallennettava-asia
                   :hoidonjohtopalkkio "laskutettava-tyo"
                   :toimistokulut "laskutettava-tyo"
                   :erillishankinnat "laskutettava-tyo"
                   :rahavaraus-lupaukseen-1 "muut-rahavaraukset"
                   :kolmansien-osapuolten-aiheuttamat-vahingot "vahinkojen-korjaukset"
                   :akilliset-hoitotyot "akillinen-hoitotyo"
                   :toimenpiteen-maaramitattavat-tyot "laskutettava-tyo")
          tehtava (case tallennettava-asia
                    :hoidonjohtopalkkio "Hoitourakan työnjohto"
                    :toimistokulut "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                    :erillishankinnat nil
                    :rahavaraus-lupaukseen-1 "Tilaajan rahavaraus lupaukseen 1"
                    :kolmansien-osapuolten-aiheuttamat-vahingot "Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen"
                    :akilliset-hoitotyot "Äkillinen hoitotyö"
                    :toimenpiteen-maaramitattavat-tyot nil)
          tehtavaryhma (cond
                         (= :erillishankinnat tallennettava-asia) "ERILLISHANKINNAT"
                         (= :rahavaraus-lupaukseen-1 tallennettava-asia) "TILAAJAN RAHAVARAUS"
                         :else nil)
          toimenpide (get toimenpiteet
                          toimenpide-avain)]
      (tallenna-kustannusarvioitu-tyo! db user {:tyyppi tyyppi
                                                :tehtava tehtava
                                                :tehtavaryhma tehtavaryhma
                                                :toimenpide toimenpide
                                                :urakka-id urakka-id
                                                :ajat ajat
                                                :summa summa}))))


(s/def ::vuosi integer?)
(s/def ::kuukausi integer?)
;(s/def ::yksikkohinta integer?)
(s/def ::urakka-id integer?)
;(s/def ::tehtava string?)
(s/def ::toimenkuva string?)
(s/def ::maksukausi keyword?)
(s/def ::hoitokausi integer?)
(s/def ::tunnit number?)
(s/def ::tuntipalkka number?)
(s/def ::kk-v number?)
(s/def ::summa number?)
(s/def ::toimenpide-avain (s/and keyword?
                                 (fn [k]
                                   (some #(= k %)
                                         toimenpide-avaimet))))
(s/def ::tallennettava-asia tallennettava-asia)

#_(s/def ::yksikkohintainen-tyo (s/keys :req-un [::vuosi ::yksikkohinta]))
#_(s/def ::tyot (s/coll-of ::yksikkohintainen-tyo))

(s/def ::jhk (s/keys :req-un [::hoitokausi ::tunnit ::tuntipalkka ::kk-v]))
(s/def ::jhkt (s/coll-of ::jhk))

(s/def ::ajat (s/coll-of (s/keys :req-un [::vuosi]
                                 :opt-un [::kuukausi])))

#_(s/def ::tallenna-yksikkohintainen-tyo-kysely (s/keys :req-un [::urakka-id ::tehtava ::tyot]))
#_(s/def ::tallenna-yksikkohintainen-tyo-vastaus any?)

(s/def ::tallenna-johto-ja-hallintokorvaukset-kysely (s/keys :req-un [::urakka-id ::toimenkuva ::maksukausi ::jhkt]))
(s/def ::tallenna-johto-ja-hallintokorvaukset-vastaus any?)

(s/def ::tallenna-kustannusarvioitu-tyo-kysely (s/keys :req-un [::urakka-id ::tallennettava-asia ::toimenpide-avain ::summa ::ajat]))
(s/def ::tallenna-kustannusarvioitu-tyo-vastaus any?)

(s/def ::tallenna-kiinteahintaiset-tyot-kysely (s/keys :req-un [::urakka-id ::toimenpide-avain ::ajat ::summa]))
(s/def ::tallenna-kiinteahintaiset-tyot-vastaus any?)

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
          #_(julkaise-palvelu
            :tallenna-yksikkohintainen-tyo
            (fn [user tiedot]
              (tallenna-yksikkohintainen-tyo db user tiedot))
            {:kysely-spec ::tallenna-yksikkohintainen-tyo-kysely
             :vastaus-spec ::tallenna-yksikkohintainen-tyo-vastaus})

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
    (poista-palvelu (:http-palvelin this) :budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :budjettitavoite)
    (poista-palvelu (:http-palvelin this) :tallenna-budjettitavoite)
    this))
