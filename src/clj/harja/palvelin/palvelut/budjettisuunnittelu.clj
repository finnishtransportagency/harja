(ns harja.palvelin.palvelut.budjettisuunnittelu
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.string :as clj-str]
            [specql.core :refer [fetch update! insert!]]
            [specql.op :as op]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.pvm :as pvm]
            [harja.kyselyt
             [budjettisuunnittelu :as q]
             [urakat :as urakat-q]
             [kiinteahintaiset-tyot :as kiin-q]
             [kustannusarvioidut-tyot :as ka-q]
             [toimenpideinstanssit :as tpi-q]
             [indeksit :as i-q]
             [konversio :as konv]]
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
          (if (map? v_)
            (when (key-from-val v_ v)
              k)
            (when (= v v_)
              k)))
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
   :toimenpiteen-maaramitattavat-tyot          "laskutettava-tyo"
   :tilaajan-varaukset                         "laskutettava-tyo"})

(defn- tyyppi->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tyyppi v))

(def ^{:private true} tallennettava-asia->tehtava
  {:hoidonjohtopalkkio                         "53647ad8-0632-4dd3-8302-8dfae09908c8" ;"c9712637-fbec-4fbd-ac13-620b5619c744"
   :toimistokulut                              "8376d9c4-3daf-4815-973d-cd95ca3bb388"
   :kolmansien-osapuolten-aiheuttamat-vahingot {:talvihoito               "49b7388b-419c-47fa-9b1b-3797f1fab21d"
                                                :liikenneympariston-hoito "63a2585b-5597-43ea-945c-1b25b16a06e2"
                                                :sorateiden-hoito         "b3a7a210-4ba6-4555-905c-fef7308dc5ec"}
   :akilliset-hoitotyot                        {:talvihoito               "1f12fe16-375e-49bf-9a95-4560326ce6cf"
                                                :liikenneympariston-hoito "1ed5d0bb-13c7-4f52-91ee-5051bb0fd974"
                                                :sorateiden-hoito         "d373c08b-32eb-4ac2-b817-04106b862fb1"}})

(defn- tehtava->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtava v))

(def ^{:private true} tallennettava-asia->tehtavaryhma
  {:erillishankinnat        "37d3752c-9951-47ad-a463-c1704cf22f4c"
   ;:hoidonjohtopalkkio      "0ef0b97e-1390-4d6c-bbc4-b30536be8a68"
   :rahavaraus-lupaukseen-1 "0e78b556-74ee-437f-ac67-7a03381c64f6"
   :tilaajan-varaukset      "a6614475-1950-4a61-82c6-fda0fd19bb54"})

(defn- tehtavaryhma->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtavaryhma v))

(defn hae-urakan-tavoite
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (q/hae-budjettitavoite db {:urakka urakka-id}))

(defn hae-urakan-indeksikertoimet
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
                                                                                    (>= vuosi urakan-alkuvuosi))))
                                                                     (remove (fn [{:keys [vuosi]}]
                                                                               (>= vuosi urakan-loppuvuosi)))
                                                                     (map (fn [{:keys [arvo vuosi]}]
                                                                            {:vuosi          vuosi
                                                                             :indeksikerroin (pyorista (/ arvo perusluku) 6)})))
                                                               (i-q/hae-indeksi db {:nimi indeksi}))
                                  urakan-indeksien-maara (count indeksiluvut-urakan-aikana)]
                              (if (= 5 urakan-indeksien-maara)
                                (vec indeksiluvut-urakan-aikana)
                                (mapv (fn [index]
                                        (if (empty? indeksiluvut-urakan-aikana)
                                          ;; VHAR-2484 urakoille, jotka eivät ole vielä alkaneet, indeksikertoimeksi 1
                                          {:vuosi          (+ urakan-alkuvuosi index)
                                           :indeksikerroin 1.00}
                                          (if (> (inc index) urakan-indeksien-maara)
                                            (nth indeksiluvut-urakan-aikana (dec urakan-indeksien-maara))
                                            (nth indeksiluvut-urakan-aikana index))))
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

(defn hae-urakan-kiinteahintaiset-tyot [db user urakka-id]
  (let [kiinteahintaiset-tyot (kiinthint-tyot/hae-urakan-kiinteahintaiset-tyot db user urakka-id)]
    (map (fn [tyo]
           (-> tyo
               (assoc :toimenpide-avain (toimenpide->toimenpide-avain (:toimenpiteen-koodi tyo)))
               (dissoc :toimenpideinstanssi :toimenpiteen-koodi)))
         kiinteahintaiset-tyot)))

(defn hae-urakan-kustannusarvoidut-tyot
  [db user urakka-id]
  (let [kustannusarvoidut-tyot (kustarv-tyot/hae-urakan-kustannusarvoidut-tyot-nimineen db user urakka-id)]
    (map (fn [tyo]
           (-> tyo
               (assoc :toimenpide-avain (toimenpide->toimenpide-avain (:toimenpiteen-koodi tyo)))
               (assoc :haettu-asia (or (tehtava->tallennettava-asia (:tehtavan-tunniste tyo))
                                       (tehtavaryhma->tallennettava-asia (:tehtavaryhman-tunniste tyo))))
               (dissoc :toimenpiteen-koodi :tehtavan-tunniste :tehtavaryhman-tunniste)))
         kustannusarvoidut-tyot)))

(defn urakan-johto-ja-hallintokorvausten-datan-rikastaminen [data]
  (let [to-float #(when % (float %))
        johto-ja-hallintokorvaukset (map (fn [johto-ja-hallintokorvaus]
                                           (-> johto-ja-hallintokorvaus
                                               (update :tunnit to-float)
                                               (update :tuntipalkka to-float)
                                               (update :maksukuukaudet konv/pgarray->vector)))
                                         data)
        hoitokauden-numero-lisatty (apply concat
                                          (map-indexed (fn [index [_ tiedot]]
                                                         (map (fn [data]
                                                                (if (:ennen-urakkaa data)
                                                                  (assoc data :hoitokausi 0)
                                                                  (assoc data :hoitokausi (inc index))))
                                                              tiedot))
                                                       (into (sorted-map)
                                                             (group-by #(pvm/paivamaaran-hoitokausi (pvm/luo-pvm (:vuosi %) (dec (:kuukausi %)) 15))
                                                                       johto-ja-hallintokorvaukset))))
        maksukausi-lisatty (reduce (fn [johto-ja-hallintokorvaukset {:keys [toimenkuva kuukausi ennen-urakkaa] :as johto-ja-hallintokorvaus}]
                                     (cond
                                       ennen-urakkaa (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi nil))
                                       (or (= toimenkuva "päätoiminen apulainen")
                                           (= toimenkuva "apulainen/työnjohtaja")) (if (<= 5 kuukausi 9)
                                                                                     (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :kesa))
                                                                                     (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :talvi)))
                                       :else (conj johto-ja-hallintokorvaukset (assoc johto-ja-hallintokorvaus :maksukausi :molemmat))))
                                   [] hoitokauden-numero-lisatty)
        kk-v-lisatty (map (fn [{:keys [#_kk-v toimenkuva maksukausi hoitokausi] :as johto-ja-hallintokorvaus}]
                            (cond
                              #_#_(not (nil? kk-v)) (update johto-ja-hallintokorvaus :kk-v float)
                              (= :kesa maksukausi) (assoc johto-ja-hallintokorvaus :kk-v 5)
                              (= :talvi maksukausi) (assoc johto-ja-hallintokorvaus :kk-v 7)
                              (and (= toimenkuva "hankintavastaava") (= 0 hoitokausi)) (assoc johto-ja-hallintokorvaus :kk-v 4.5)
                              (= toimenkuva "viherhoidosta vastaava henkilö") (assoc johto-ja-hallintokorvaus :kk-v 5)
                              (= toimenkuva "harjoittelija") (assoc johto-ja-hallintokorvaus :kk-v 4)
                              :else (assoc johto-ja-hallintokorvaus :kk-v 12)))
                          maksukausi-lisatty)]
    kk-v-lisatty))

(defn hae-urakan-johto-ja-hallintokorvaukset [db urakka-id]
  (urakan-johto-ja-hallintokorvausten-datan-rikastaminen (q/hae-johto-ja-hallintokorvaukset db {:urakka-id urakka-id})))

(defn hae-urakan-omat-johto-ja-hallintokorvaukset [db urakka-id]
  (urakan-johto-ja-hallintokorvausten-datan-rikastaminen (q/hae-omat-johto-ja-hallintokorvaukset db {:urakka-id urakka-id})))

(defn kasittele-urakan-johto-ja-hallintokorvauksien-haku! [db urakka-id]
  (jdbc/with-db-transaction [db db]
                            (let [jh-korvaukset (hae-urakan-johto-ja-hallintokorvaukset db urakka-id)
                                  omat-jh-korvaukset (hae-urakan-omat-johto-ja-hallintokorvaukset db urakka-id)
                                  urakan-omat-jh-korvaukset (q/hae-urakan-omat-jh-korvaukset db {:urakka-id urakka-id})
                                  jh-korvausten-omiariveja-lkm 2
                                  luotujen-toimenkuvien-idt (when (> jh-korvausten-omiariveja-lkm (count urakan-omat-jh-korvaukset))
                                                              (for [_ (range 0 (- jh-korvausten-omiariveja-lkm (count urakan-omat-jh-korvaukset)))]
                                                                (:id (q/lisaa-oma-johto-ja-hallintokorvaus-toimenkuva<! db {:toimenkuva nil :urakka-id urakka-id}))))]
                              {:vakiot           jh-korvaukset
                               :omat             omat-jh-korvaukset
                               :omat-toimenkuvat (vec (concat urakan-omat-jh-korvaukset (map #(identity {:toimenkuva-id % :toimenkuva nil}) luotujen-toimenkuvien-idt)))})))

(defn hae-urakan-budjetoidut-tyot
  "Palvelu, joka palauttaa urakan budjetoidut työt. Palvelu palauttaa kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset työt mapissa jäsenneltynä."
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  {:kiinteahintaiset-tyot       (hae-urakan-kiinteahintaiset-tyot db user urakka-id)
   :kustannusarvioidut-tyot     (hae-urakan-kustannusarvoidut-tyot db user urakka-id)
   :johto-ja-hallintokorvaukset (kasittele-urakan-johto-ja-hallintokorvauksien-haku! db urakka-id)})

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
         (or (nil? summa)
             (number? summa))]}
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
                              (kiin-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})
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

(defn tallenna-johto-ja-hallintokorvaukset
  [db user {:keys [urakka-id toimenkuva toimenkuva-id ennen-urakkaa? jhk-tiedot maksukausi]}]
  {:pre [(integer? urakka-id)
         (or (and toimenkuva-id (integer? toimenkuva-id))
             (and toimenkuva (string? toimenkuva)))]}
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [toimenkuva-id (or toimenkuva-id
                                                    (::bs/id (first (fetch db ::bs/johto-ja-hallintokorvaus-toimenkuva
                                                                           #{::bs/id}
                                                                           {::bs/toimenkuva toimenkuva}))))
                                  maksukuukaudet (::bs/maksukuukaudet (first (fetch db ::bs/johto-ja-hallintokorvaus-toimenkuva
                                                                                    #{::bs/maksukuukaudet}
                                                                                    {::bs/toimenkuva toimenkuva})))
                                  toimenkuvan-urakka-id (::bs/urakka-id (first (fetch db ::bs/johto-ja-hallintokorvaus-toimenkuva
                                                                                      #{::bs/urakka-id}
                                                                                      {::bs/id toimenkuva-id})))
                                  _ (when-not (or (nil? toimenkuvan-urakka-id) (= urakka-id toimenkuvan-urakka-id))
                                      (throw (Exception. "Yritetään tallentaa toisen urakan toimenkuvalle")))
                                  _ (when (nil? toimenkuva-id)
                                      (throw (Exception. (str "Annettu toimenkuva ei löydy kannasta!\n Toimenkuva: " toimenkuva))))
                                  toimenpideinstanssi-id (:id (first (tpi-q/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db {:urakka urakka-id
                                                                                                                                  :koodi  (toimenpide-avain->toimenpide :mhu-johto)})))
                                  olemassa-olevat-jhkt (fetch db ::bs/johto-ja-hallintokorvaus
                                                              #{::bs/id ::bs/toimenkuva-id ::bs/vuosi ::bs/kuukausi ::bs/ennen-urakkaa}
                                                              {::bs/urakka-id     urakka-id
                                                               ::bs/toimenkuva-id toimenkuva-id
                                                               ::bs/vuosi         (op/in (distinct (map :vuosi jhk-tiedot)))
                                                               ::bs/ennen-urakkaa ennen-urakkaa?})
                                  olemassa-olevat-jhkt (filter (fn [{::bs/keys [vuosi kuukausi] :as jhk}]
                                                                 (some #(when (and (= vuosi (:vuosi %))
                                                                                   (= kuukausi (:kuukausi %)))
                                                                          jhk)
                                                                       jhk-tiedot))
                                                               olemassa-olevat-jhkt)
                                  uudet-jhkt (remove (fn [{:keys [vuosi kuukausi]}]
                                                       (some #(and (= vuosi (::bs/vuosi %))
                                                                   (= kuukausi (::bs/kuukausi %)))
                                                             olemassa-olevat-jhkt))
                                                     jhk-tiedot)]
                              (ka-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})
                              (kiin-q/merkitse-maksuerat-likaisiksi-hoidonjohdossa! db {:toimenpideinstanssi toimenpideinstanssi-id})
                              (when (and maksukausi
                                         (not (= (bs-p/maksukauden-kuukaudet maksukausi)
                                                 maksukuukaudet)))
                                (update! db
                                         ::bs/johto-ja-hallintokorvaus-toimenkuva
                                         {::bs/maksukuukaudet (bs-p/maksukauden-kuukaudet maksukausi)}
                                         {::bs/id toimenkuva-id}))
                              (when-not (empty? olemassa-olevat-jhkt)
                                (doseq [jhk olemassa-olevat-jhkt
                                        :let [tunnit (some (fn [{:keys [vuosi kuukausi tunnit]}]
                                                             (when (and (= vuosi (::bs/vuosi jhk))
                                                                        (= kuukausi (::bs/kuukausi jhk)))
                                                               tunnit))
                                                           jhk-tiedot)
                                              tuntipalkka (some (fn [{:keys [vuosi kuukausi tuntipalkka]}]
                                                                  (when (and (= vuosi (::bs/vuosi jhk))
                                                                             (= kuukausi (::bs/kuukausi jhk)))
                                                                    tuntipalkka))
                                                                jhk-tiedot)]]
                                  (update! db
                                           ::bs/johto-ja-hallintokorvaus
                                           {::bs/tunnit      tunnit
                                            ::bs/tuntipalkka tuntipalkka
                                            ::bs/muokattu    (pvm/nyt)
                                            ::bs/muokkaaja   (:id user)}
                                           {::bs/id (::bs/id jhk)})))
                              (when-not (empty? uudet-jhkt)
                                (doseq [{:keys [vuosi kuukausi osa-kuukaudesta tunnit tuntipalkka]} uudet-jhkt]
                                  (insert! db
                                           ::bs/johto-ja-hallintokorvaus
                                           {::bs/urakka-id       urakka-id
                                            ::bs/toimenkuva-id   toimenkuva-id
                                            ::bs/tunnit          tunnit
                                            ::bs/tuntipalkka     tuntipalkka
                                            ::bs/kuukausi        kuukausi
                                            ::bs/vuosi           vuosi
                                            ::bs/ennen-urakkaa   ennen-urakkaa?
                                            ::bs/osa-kuukaudesta osa-kuukaudesta
                                            ::bs/luotu           (pvm/nyt)
                                            ::bs/luoja           (:id user)})))
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
                                                                        #{::tpk/id}
                                                                        {::tpk/yksiloiva-tunniste tehtava})))
                                  {toimenpide-id ::tpk/id} (first (fetch db ::tpk/toimenpidekoodi
                                                                         #{::tpk/id}
                                                                         {::tpk/taso  3
                                                                          ::tpk/koodi toimenpide}))
                                  {tehtavaryhma-id ::tr/id} (when tehtavaryhma
                                                              (first (fetch db ::tr/tehtavaryhma
                                                                            #{::tr/id}
                                                                            {::tr/yksiloiva-tunniste tehtavaryhma})))
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
                                  tehtava (if (map? tehtava)
                                            (get tehtava toimenpide-avain)
                                            tehtava)
                                  tehtavaryhma (tallennettava-asia->tehtavaryhma tallennettava-asia)
                                  toimenpide (toimenpide-avain->toimenpide toimenpide-avain)]
                              (tallenna-kustannusarvioitu-tyo! db user {:tyyppi       tyyppi
                                                                        :tehtava      tehtava
                                                                        :tehtavaryhma tehtavaryhma
                                                                        :toimenpide   toimenpide
                                                                        :urakka-id    urakka-id
                                                                        :ajat         ajat
                                                                        :summa        summa}))))

(defn tallenna-toimenkuva
  [db user {:keys [urakka-id toimenkuva-id toimenkuva]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [paivitettyjen-rivien-maara (update! db
                                            ::bs/johto-ja-hallintokorvaus-toimenkuva
                                            {::bs/toimenkuva toimenkuva}
                                            {::bs/id        toimenkuva-id
                                             ::bs/urakka-id urakka-id})]
    (if (= 0 paivitettyjen-rivien-maara)
      {:virhe "Yhtään riviä ei päivitetty"}
      {:onnistui? true})))

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
                                             (hae-urakan-indeksikertoimet db user tiedot))
            {:kysely-spec  ::bs-p/budjettisuunnittelun-indeksit-kysely
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
             :vastaus-spec ::bs-p/tallenna-kustannusarvioitu-tyo-vastaus})
          (julkaise-palvelu
            :tallenna-toimenkuva
            (fn [user tiedot]
              (tallenna-toimenkuva db user tiedot))
            {:kysely-spec  ::bs-p/tallenna-toimenkuva-kysely
             :vastaus-spec ::bs-p/tallenna-toimenkuva-vastaus}))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :budjetoidut-tyot
                     :budjettitavoite
                     :budjettisuunnittelun-indeksit
                     :tallenna-budjettitavoite
                     :tallenna-kiinteahintaiset-tyot
                     :tallenna-johto-ja-hallintokorvaukset
                     :tallenna-kustannusarvioitu-tyo
                     :tallenna-toimenkuva)
    this))
