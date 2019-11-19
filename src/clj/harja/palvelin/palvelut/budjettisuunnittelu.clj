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
             [kiinteahintaiset-tyot :as kiin-q]
             [kustannusarvioidut-tyot :as ka-q]
             [toimenpideinstanssit :as tpi-q]
             [indeksit :as i-q]]
            [harja.palvelin.palvelut
             [kiinteahintaiset-tyot :as kiinthint-tyot]
             [kustannusarvioidut-tyot :as kustarv-tyot]]
            [harja.domain
             [oikeudet :as oikeudet]
             [budjettisuunnittelu :as bs]
             [toimenpidekoodi :as tpk]
             [tehtavaryhma :as tr]
             [urakka :as ur]]
            [harja.domain.palvelut.budjettisuunnittelu :as bs-p]))

(defn- key-from-val [m v]
  (some (fn [[k v_]]
          (when (= v v_)
            k))
        m))

(def ^{:private true} toimenpide-avain->toimenpide
  {:paallystepaikkaukset     "20107"
   :mhu-yllapito             "20191"
   :talvihoito               "23104"
   :liikenneympariston-hoito "23116"
   :sorateiden-hoito         "23124"
   :mhu-korvausinvestointi   "14301"
   :mhu-johto                "23151"})

(defn- toimenpide->toimenpide-avain [v]
  (key-from-val toimenpide-avain->toimenpide v))

(def ^{:private true} tallennettava-asia->tyyppi
  {:hoidonjohtopalkkio                         "laskutettava-tyo"
   :toimistokulut                              "laskutettava-tyo"
   :erillishankinnat                           "laskutettava-tyo"
   :rahavaraus-lupaukseen-1                    "muut-rahavaraukset"
   :kolmansien-osapuolten-aiheuttamat-vahingot "vahinkojen-korjaukset"
   :akilliset-hoitotyot                        "akillinen-hoitotyo"
   :toimenpiteen-maaramitattavat-tyot          "laskutettava-tyo"})

(defn- tyyppi->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tyyppi v))

(def ^{:private true} tallennettava-asia->tehtava
  {:hoidonjohtopalkkio                         "Hoitourakan työnjohto"
   :toimistokulut                              "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
   :kolmansien-osapuolten-aiheuttamat-vahingot "Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen"
   :akilliset-hoitotyot                        "Äkillinen hoitotyö"})

(defn- tehtava->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtava v))

(def ^{:private true} tallennettava-asia->tehtavaryhma
  {:erillishankinnat        "ERILLISHANKINNAT (W)"
   :rahavaraus-lupaukseen-1 "TILAAJAN RAHAVARAUS (T3)"})

(defn- tehtavaryhma->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtavaryhma v))

(defn hae-urakan-tavoite
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (q/hae-budjettitavoite db {:urakka urakka-id}))

(defn hae-urakan-indeksit
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [pyorista #(/ (Math/round (* %1 (Math/pow 10 %2))) (Math/pow 10 %2))
          {::ur/keys [alkupvm loppupvm indeksi]} (first (fetch db
                                                                   ::ur/urakka
                                                                   #{::ur/alkupvm ::ur/loppupvm ::ur/indeksi}
                                                                   {::ur/id urakka-id}))
          urakan-alkuvuosi (-> alkupvm pvm/joda-timeksi pvm/suomen-aikavyohykkeeseen pvm/vuosi)
          urakan-loppuvuosi (-> loppupvm pvm/joda-timeksi pvm/suomen-aikavyohykkeeseen pvm/vuosi)
          perusluku (float (:perusluku (first (i-q/hae-urakan-indeksin-perusluku db {:urakka-id urakka-id}))))
          indeksiluvut-urakan-aikana (sequence
                                       (comp (filter (fn [{:keys [kuukausi vuosi]}]
                                                       (and (= 9 kuukausi)
                                                            (>= vuosi (dec urakan-alkuvuosi)))))
                                             (remove (fn [{:keys [vuosi]}]
                                                       (>= vuosi (dec urakan-loppuvuosi))))
                                             (map (fn [{:keys [arvo vuosi]}]
                                                    ;; Vuoden indeksi lasketaan edellisen vuoden arvoista
                                                    {:vuosi (inc vuosi)
                                                     :indeksikorjaus (pyorista (/ arvo perusluku) 6)})))
                                       (i-q/hae-indeksi db {:nimi indeksi}))
          urakan-indeksien-maara (count indeksiluvut-urakan-aikana)]
      (if (= 5 urakan-indeksien-maara)
        (vec indeksiluvut-urakan-aikana)
        (mapv (fn [index]
                (if (> (inc index) urakan-indeksien-maara)
                  (nth indeksiluvut-urakan-aikana (dec urakan-indeksien-maara))
                  (nth indeksiluvut-urakan-aikana index)))
              (range 0 5))))))

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
                                        (q/paivita-budjettitavoite<! c hkt))))
                              {:onnistui? true})))

(defn hae-urakan-kustannusarvoidut-tyot
  [db user urakka-id]
  (let [kustannusarvoidut-tyot (kustarv-tyot/hae-urakan-kustannusarvoidut-tyot-nimineen db user urakka-id)]
    (map (fn [tyo]
           (-> tyo
               (assoc :toimenpide-avain (toimenpide->toimenpide-avain (:toimenpiteen-koodi tyo)))
               (assoc :haettu-asia (or (tehtava->tallennettava-asia (:tehtavan-nimi tyo))
                                       (tehtavaryhma->tallennettava-asia (:tehtavaryhman-nimi tyo))))
               (dissoc :toimenpiteen-koodi :tehtavan-nimi :tehtavaryhman-nimi)))
         kustannusarvoidut-tyot)))

(defn hae-urakan-johto-ja-hallintokorvaukset [db urakka-id]
  (let [johto-ja-hallintokorvaukset (map (fn [johto-ja-hallintokorvaus]
                                           (-> johto-ja-hallintokorvaus
                                               (update :tunnit float)
                                               (update :tuntipalkka float)))
                                         (q/hae-johto-ja-hallintokorvaukset db {:urakka-id urakka-id}))
        hoitokauden-numero-lisatty (apply concat
                                          (map-indexed (fn [index [hoitokausi tiedot]]
                                                         (map (fn [data]
                                                                (if-let [kk-v (:kk-v data)]
                                                                  (assoc data :hoitokausi 0)
                                                                  (assoc data :hoitokausi (inc index))))
                                                              tiedot))
                                                       (into (sorted-map)
                                                             (group-by #(pvm/paivamaaran-hoitokausi (pvm/luo-pvm (:vuosi %) (dec (:kuukausi %)) 15))
                                                                       johto-ja-hallintokorvaukset))))
        maksukausi-lisatty (reduce (fn [johto-ja-hallintokorvaukset {:keys [toimenkuva kuukausi] :as johto-ja-hallintokorvaus}]
                                     (if (or (= toimenkuva "päätoiminen apulainen")
                                             (= toimenkuva "apulainen/työnjohtaja"))
                                       (if (<= 5 kuukausi 9)
                                           (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :kesa))
                                           (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :talvi)))
                                       (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :molemmat))))
                                      [] hoitokauden-numero-lisatty)
        tunnit-korjattu (map (fn [{:keys [hoitokausi kk-v] :as johto-ja-hallintokorvaus}]
                               (if (= 0 hoitokausi)
                                 (update johto-ja-hallintokorvaus :tunnit (fn [tunnit]
                                                                            (/ tunnit kk-v)))
                                 johto-ja-hallintokorvaus))
                             maksukausi-lisatty)
        kk-v-lisatty (map (fn [{:keys [kk-v toimenkuva maksukausi] :as johto-ja-hallintokorvaus}]
                            (cond
                              (not (nil? kk-v)) (update johto-ja-hallintokorvaus :kk-v float)
                              (= :kesa maksukausi) (assoc johto-ja-hallintokorvaus :kk-v 5)
                              (= :talvi maksukausi) (assoc johto-ja-hallintokorvaus :kk-v 7)
                              (= toimenkuva "viherhoidosta vastaava henkilö") (assoc johto-ja-hallintokorvaus :kk-v 5)
                              (= toimenkuva "harjoittelija") (assoc johto-ja-hallintokorvaus :kk-v 4)
                              :else (assoc johto-ja-hallintokorvaus :kk-v 12)))
                          tunnit-korjattu)
        tarvittavat-tiedot (map (fn [johto-ja-hallintokorvaus]
                                  (select-keys johto-ja-hallintokorvaus
                                               #{:kk-v :hoitokausi :toimenkuva :tunnit :tuntipalkka :maksukausi}))
                                kk-v-lisatty)]
    (distinct tarvittavat-tiedot)))

(defn hae-urakan-budjetoidut-tyot
  "Palvelu, joka palauttaa urakan budjetoidut työt. Palvelu palauttaa kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset työt mapissa jäsenneltynä."
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  {:kiinteahintaiset-tyot       (kiinthint-tyot/hae-urakan-kiinteahintaiset-tyot db user urakka-id)
   :kustannusarvioidut-tyot     (hae-urakan-kustannusarvoidut-tyot db user urakka-id)
   :johto-ja-hallintokorvaukset (hae-urakan-johto-ja-hallintokorvaukset db urakka-id)})

(defn- mudosta-ajat
  "Oletuksena, että jos pelkästään vuosi on annettuna kuukauden sijasta, kyseessä on hoitokauden aloitusvuosi"
  [ajat]
  (reduce (fn [ajat {:keys [vuosi kuukausi]}]
            (if kuukausi
              (conj ajat {:vuosi    vuosi
                          :kuukausi kuukausi})
              (let [ajat-valille (fn [vuosi [alku loppu]]
                                   (map #(identity
                                           {:vuosi    vuosi
                                            :kuukausi %})
                                        (range alku (inc loppu))))
                    hoitokauden-vuodet [vuosi (inc vuosi)]]
                (into []
                      (concat ajat
                              (ajat-valille (first hoitokauden-vuodet) [10 12])
                              (ajat-valille (second hoitokauden-vuodet) [1 9]))))))
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
                                                                         {::tpk/taso  3
                                                                          ::tpk/koodi toimenpide}))
                                  {toimenpideinstanssi-id :id} (first (tpi-q/hae-urakan-toimenpideinstanssi db {:urakka urakka-id :tp toimenpide-id}))
                                  _ (when (nil? toimenpideinstanssi-id)
                                      (throw (Exception. "Toimenpideinstanssia ei löydetty")))

                                  ajat (mudosta-ajat ajat)

                                  olemassa-olevat-kiinteahintaiset-tyot-vuosille (fetch db ::bs/kiinteahintainen-tyo
                                                                                        #{::bs/id ::bs/smallint-v ::bs/smallint-kk}
                                                                                        {::bs/smallint-v          (op/in (into #{} (distinct (map :vuosi ajat))))
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
                              (kiin-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:urakka              urakka-id
                                                                                     :toimenpideinstanssi toimenpideinstanssi-id})
                              (when-not (empty? olemassa-olevat-kiinteahintaiset-tyot)
                                (doseq [olemassa-oleva-tyo olemassa-olevat-kiinteahintaiset-tyot]
                                  (update! db ::bs/kiinteahintainen-tyo
                                           {::bs/summa     summa
                                            ::bs/muokattu  (pvm/nyt)
                                            ::bs/muokkaaja (:id user)}
                                           {::bs/id (::bs/id olemassa-oleva-tyo)})))
                              (when-not (empty? uudet-kiinteahintaiset-tyot-ajat)
                                (let [paasopimus (urakat-q/urakan-paasopimus-id db urakka-id)]
                                  (doseq [{:keys [vuosi kuukausi]} uudet-kiinteahintaiset-tyot-ajat]
                                    (insert! db ::bs/kiinteahintainen-tyo
                                             {::bs/smallint-v          vuosi
                                              ::bs/smallint-kk         kuukausi
                                              ::bs/summa               summa
                                              ::bs/toimenpideinstanssi toimenpideinstanssi-id
                                              ::bs/sopimus             paasopimus
                                              ::bs/luotu               (pvm/nyt)
                                              ::bs/luoja               (:id user)}))))
                              {:onnistui? true})))

(defn muunna-jhkn-ajat
  [toimenkuva maksukausi jhkt urakan-aloitusvuosi]
  (let [urakan-aikaiset-jhkt (fn [jhkt kuukaudet]
                               (mapcat (fn [{:keys [tunnit tuntipalkka hoitokausi]}]
                                         (map (fn [kuukausi]
                                                (let [vuosi (if (<= 10 kuukausi 12)
                                                              (dec (+ urakan-aloitusvuosi hoitokausi))
                                                              (+ urakan-aloitusvuosi hoitokausi))]
                                                  {:kuukausi kuukausi
                                                   :vuosi vuosi
                                                   :tunnit tunnit
                                                   :tuntipalkka tuntipalkka}))
                                              kuukaudet))
                                       jhkt))]
    (cond
      (= toimenkuva "harjoittelija") (urakan-aikaiset-jhkt jhkt (range 5 9))
      (= toimenkuva "viherhoidosta vastaava henkilö") (urakan-aikaiset-jhkt jhkt (range 4 9))
      (= toimenkuva "hankintavastaava") (mapcat (fn [{:keys [tunnit tuntipalkka hoitokausi kk-v]}]
                                                  (if (= 0 hoitokausi)
                                                    [{:kuukausi 10
                                                      :vuosi urakan-aloitusvuosi
                                                      :tunnit (* tunnit kk-v)
                                                      :tuntipalkka tuntipalkka
                                                      :ennen-urakkaa? true
                                                      :kk-v kk-v}]
                                                    (map (fn [kuukausi]
                                                           {:kuukausi kuukausi
                                                            :vuosi (if (<= 10 kuukausi 12)
                                                                     (dec (+ urakan-aloitusvuosi hoitokausi))
                                                                     (+ urakan-aloitusvuosi hoitokausi))
                                                            :tunnit tunnit
                                                            :tuntipalkka tuntipalkka})
                                                         (range 1 13))))
                                                jhkt)
      (= maksukausi :kesa) (urakan-aikaiset-jhkt jhkt (range 5 10))
      (= maksukausi :talvi) (urakan-aikaiset-jhkt jhkt (concat (range 10 13) (range 1 5)))
      :else (urakan-aikaiset-jhkt jhkt (range 1 13)))))

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
                                  toimenpideinstanssi-id (:id (first (tpi-q/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db {:urakka urakka-id
                                                                                                                                  :koodi  (toimenpide-avain->toimenpide :mhu-johto)})))
                                  {urakan-alkupvm ::ur/alkupvm} (first (fetch db ::ur/urakka
                                                                              #{::ur/alkupvm}
                                                                              {::ur/id urakka-id}))
                                  jhkt (muunna-jhkn-ajat toimenkuva maksukausi jhkt (pvm/vuosi urakan-alkupvm))
                                  olemassa-olevat-jhkt (fetch db ::bs/johto-ja-hallintokorvaus
                                                              #{::bs/id ::bs/toimenkuva-id ::bs/vuosi ::bs/kuukausi ::bs/ennen-urakkaa-id}
                                                              {::bs/urakka-id urakka-id
                                                               ::bs/toimenkuva-id toimenkuva-id
                                                               ::bs/vuosi (op/in (map :vuosi jhkt))
                                                               ::bs/kuukausi (op/in (map :kuukausi jhkt))
                                                               ::bs/ennen-urakkaa-id (if (some :ennen-urakkaa?
                                                                                               jhkt)
                                                                                       op/not-null?
                                                                                       op/null?)})
                                  paivitetaan? (not (empty? olemassa-olevat-jhkt))]
                              (ka-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})
                              (if paivitetaan?
                                (doseq [{:keys [kuukausi vuosi tunnit tuntipalkka ennen-urakkaa?]} jhkt]
                                  (let [id (some #(when (and (= toimenkuva-id (::bs/toimenkuva-id %))
                                                             (= vuosi (::bs/vuosi %))
                                                             (= kuukausi (::bs/kuukausi %))
                                                             (or (and ennen-urakkaa?
                                                                      (not (nil? (::bs/ennen-urakkaa-id %))))
                                                                 (and (not ennen-urakkaa?)
                                                                      (nil? (::bs/ennen-urakkaa-id %)))))
                                                    (::bs/id %))
                                                 olemassa-olevat-jhkt)]
                                    (update! db
                                             ::bs/johto-ja-hallintokorvaus
                                             {::bs/tunnit      tunnit
                                              ::bs/tuntipalkka tuntipalkka
                                              ::bs/muokattu    (pvm/nyt)
                                              ::bs/muokkaaja   (:id user)}
                                             {::bs/id id})))
                                (doseq [{:keys [kuukausi vuosi tunnit tuntipalkka ennen-urakkaa? kk-v]} jhkt]
                                  (let [{ennen-urakkaa-id ::bs/id} (when ennen-urakkaa?
                                                                     (insert! db
                                                                              ::bs/johto-ja-hallintokorvaus-ennen-urakkaa
                                                                              {::bs/kk-v kk-v}))]
                                    (insert! db
                                             ::bs/johto-ja-hallintokorvaus
                                             {::bs/urakka-id urakka-id
                                              ::bs/toimenkuva-id toimenkuva-id
                                              ::bs/tunnit tunnit
                                              ::bs/tuntipalkka tuntipalkka
                                              ::bs/kuukausi kuukausi
                                              ::bs/vuosi vuosi
                                              ::bs/ennen-urakkaa-id ennen-urakkaa-id
                                              ::bs/luotu (pvm/nyt)
                                              ::bs/luoja (:id user)}))))
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
                                                                        {::tpk/nimi                 tehtava
                                                                         ::tpk/toimenpidekoodi-join {::tpk/koodi toimenpide}})))
                                  {toimenpide-id ::tpk/id} (first (fetch db ::tpk/toimenpidekoodi
                                                                         #{::tpk/id}
                                                                         {::tpk/taso  3
                                                                          ::tpk/koodi toimenpide}))
                                  {tehtavaryhma-id ::tr/id} (when tehtavaryhma
                                                              (first (fetch db ::tr/tehtavaryhma
                                                                            #{::tr/id}
                                                                            {::tr/nimi tehtavaryhma})))
                                  {toimenpideinstanssi-id :id} (first (tpi-q/hae-urakan-toimenpideinstanssi db {:urakka urakka-id :tp toimenpide-id}))
                                  _ (when (nil? toimenpideinstanssi-id)
                                      (throw (Exception. "Toimenpideinstanssia ei löydetty")))
                                  tyyppi (keyword tyyppi)
                                  ajat (mudosta-ajat ajat)
                                  kustannusarvioitu-tyo-params (into {}
                                                                     (map (fn [[k v]]
                                                                            (if (nil? v)
                                                                              [k op/null?]
                                                                              [k v]))
                                                                          {::bs/smallint-v          (op/in (into #{} (distinct (map :vuosi ajat))))
                                                                           ::bs/tehtava             tehtava-id
                                                                           ::bs/tehtavaryhma        tehtavaryhma-id
                                                                           ::bs/tyyppi              tyyppi
                                                                           ::bs/toimenpideinstanssi toimenpideinstanssi-id}))

                                  olemassa-olevat-kustannusarvioidut-tyot-vuosille (fetch db ::bs/kustannusarvioitu-tyo
                                                                                          #{::bs/id ::bs/smallint-v ::bs/smallint-kk}
                                                                                          kustannusarvioitu-tyo-params)
                                  olemassa-olevat-kustannusarvioidut-tyot (filter (fn [{::bs/keys [smallint-v smallint-kk]}]
                                                                                    (some #(and (= (:vuosi %) smallint-v)
                                                                                                (= (:kuukausi %) smallint-kk))
                                                                                          ajat))
                                                                                  olemassa-olevat-kustannusarvioidut-tyot-vuosille)
                                  uudet-kustannusarvioidut-tyot-ajat (remove (fn [{:keys [vuosi kuukausi]}]
                                                                               (some #(and (= vuosi (::bs/smallint-v %))
                                                                                           (= kuukausi (::bs/smallint-kk %)))
                                                                                     olemassa-olevat-kustannusarvioidut-tyot-vuosille))
                                                                             ajat)]
                              (ka-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})
                              (when-not (empty? olemassa-olevat-kustannusarvioidut-tyot)
                                (doseq [olemassa-oleva-tyo olemassa-olevat-kustannusarvioidut-tyot]
                                  (update! db ::bs/kustannusarvioitu-tyo
                                           {::bs/summa     summa
                                            ::bs/muokattu  (pvm/nyt)
                                            ::bs/muokkaaja (:id user)}
                                           {::bs/id (::bs/id olemassa-oleva-tyo)})))
                              (when-not (empty? uudet-kustannusarvioidut-tyot-ajat)
                                (let [paasopimus (urakat-q/urakan-paasopimus-id db urakka-id)]
                                  (doseq [{:keys [vuosi kuukausi]} uudet-kustannusarvioidut-tyot-ajat]
                                    (insert! db ::bs/kustannusarvioitu-tyo
                                             {::bs/smallint-v          vuosi
                                              ::bs/smallint-kk         kuukausi
                                              ::bs/summa               summa
                                              ::bs/tyyppi              tyyppi
                                              ::bs/tehtava             tehtava-id
                                              ::bs/tehtavaryhma        tehtavaryhma-id
                                              ::bs/toimenpideinstanssi toimenpideinstanssi-id
                                              ::bs/sopimus             paasopimus
                                              ::bs/luotu               (pvm/nyt)
                                              ::bs/luoja               (:id user)}))))
                              {:onnistui? true})))


(defn tallenna-kustannusarvioitu-tyo
  [db user {:keys [urakka-id tallennettava-asia toimenpide-avain summa ajat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [tyyppi (tallennettava-asia->tyyppi tallennettava-asia)
                                  tehtava (tallennettava-asia->tehtava tallennettava-asia)
                                  tehtavaryhma (tallennettava-asia->tehtavaryhma tallennettava-asia)
                                  toimenpide (toimenpide-avain->toimenpide toimenpide-avain)]
                              (tallenna-kustannusarvioitu-tyo! db user {:tyyppi       tyyppi
                                                                        :tehtava      tehtava
                                                                        :tehtavaryhma tehtavaryhma
                                                                        :toimenpide   toimenpide
                                                                        :urakka-id    urakka-id
                                                                        :ajat         ajat
                                                                        :summa        summa}))))

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
            :budjettisuunnittelun-indeksit (fn [user tiedot]
                                             (hae-urakan-indeksit db user tiedot))
            {:kysely-spec ::bs-p/budjettisuunnittelun-indeksit-kysely
             :vastaus-spec ::bs-p/budjettisuunnittelun-indeksit-vastaus})
          (julkaise-palvelu
            :tallenna-budjettitavoite (fn [user tiedot]
                                        (tallenna-urakan-tavoite db user tiedot))
            {:kysely-spec  ::bs-p/tallenna-budjettitavoite-kysely
             :vastaus-spec ::bs-p/tallenna-budjettitavoite-vastaus})

          (julkaise-palvelu
            :tallenna-kiinteahintaiset-tyot
            (fn [user tiedot]
              (tallenna-kiinteahintaiset-tyot db user tiedot))
            {:kysely-spec  ::bs-p/tallenna-kiinteahintaiset-tyot-kysely
             :vastaus-spec ::bs-p/tallenna-kiinteahintaiset-tyot-vastaus})
          (julkaise-palvelu
            :tallenna-johto-ja-hallintokorvaukset
            (fn [user tiedot]
              (tallenna-johto-ja-hallintokorvaukset db user tiedot))
            {:kysely-spec  ::bs-p/tallenna-johto-ja-hallintokorvaukset-kysely
             :vastaus-spec ::bs-p/tallenna-johto-ja-hallintokorvaukset-vastaus})
          (julkaise-palvelu
            :tallenna-kustannusarvioitu-tyo
            (fn [user tiedot]
              (tallenna-kustannusarvioitu-tyo db user tiedot))
            {:kysely-spec  ::bs-p/tallenna-kustannusarvioitu-tyo-kysely
             :vastaus-spec ::bs-p/tallenna-kustannusarvioitu-tyo-vastaus}))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :budjetoidut-tyot
                     :budjettitavoite
                     :budjettisuunnittelun-indeksit
                     :tallenna-budjettitavoite
                     :tallenna-kiinteahintaiset-tyot
                     :tallenna-johto-ja-hallintokorvaukset
                     :tallenna-kustannusarvioitu-tyo)
    this))
