(ns harja.palvelin.palvelut.toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.roolit :as roolit]
            [clojure.java.jdbc :as jdbc]

            [harja.kyselyt.toteumat :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]))

(def toteuma-xf
  (comp (map #(-> %
                  (konv/array->vec :tehtavat)
                  (konv/array->vec :materiaalit)))))

(def muunna-desimaaliluvut-xf
  (map #(-> %
            (assoc-in [:maara]
                      (or (some-> % :maara double) 0)))))

(defn toteuman-tehtavat->map [toteumat]
  (let [mapattu (map (fn [rivi]
         (log/debug "Mapataan rivi: " (pr-str rivi))
         (assoc rivi :tehtavat
                     (mapv (fn [tehtava]
                             (log/debug "Mapataan Tehtävä: " (pr-str tehtava))
                             (let [splitattu (str/split tehtava #"\^")]
                                           {:tehtava-id (Integer/parseInt (first splitattu))
                                            :tpk-id (Integer/parseInt (second splitattu))
                                            :nimi   (get splitattu 2)
                                            :maara  (Integer/parseInt (get splitattu 3))
                                            }))
                           (:tehtavat rivi))))
       toteumat)]
  (log/debug "Mappaus valmis: " (pr-str mapattu))
  mapattu))

(defn hae-urakan-toteumat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi]}]
  (log/debug "Haetaan urakan toteumat: " urakka-id sopimus-id alkupvm loppupvm tyyppi)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [rivit (into []
                    toteuma-xf
                    (q/listaa-urakan-toteumat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm) (name tyyppi)))]
    (toteuman-tehtavat->map rivit)))

(defn hae-urakan-toteuma [db user {:keys [urakka-id toteuma-id]}]
  (log/debug "Haetaan urakan toteuman id:llä: " toteuma-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [rivi (first (into []
                    toteuma-xf
                    (q/listaa-urakan-toteuma db urakka-id toteuma-id)))]
    (first (toteuman-tehtavat->map [rivi]))))

(defn hae-urakan-toteutuneet-tehtavat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi]}]
  (log/debug "Haetaan urakan toteutuneet tehtävät: " urakka-id sopimus-id alkupvm loppupvm tyyppi)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [toteutuneet-tehtavat (into []
        muunna-desimaaliluvut-xf
        (q/hae-urakan-toteutuneet-tehtavat db urakka-id sopimus-id (konv/sql-timestamp alkupvm) (konv/sql-timestamp loppupvm) (name tyyppi)))]
    (log/debug "Haetty urakan toteutuneet tehtävät: " toteutuneet-tehtavat)
  toteutuneet-tehtavat))

(defn hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi toimenpidekoodi]}]
  (log/debug "Haetaan urakan toteutuneet tehtävät tyypillä ja toimenpidekoodilla: " urakka-id sopimus-id alkupvm loppupvm tyyppi toimenpidekoodi)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        muunna-desimaaliluvut-xf
        (q/hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db urakka-id sopimus-id (konv/sql-timestamp alkupvm) (konv/sql-timestamp loppupvm) (name tyyppi) toimenpidekoodi)))

(defn hae-urakan-toteuma-paivat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteumapäivän: " urakka-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into #{}
        (map :paiva)
        (q/hae-urakan-toteuma-paivat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))


(defn hae-urakan-tehtavat [db user urakka-id]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (q/hae-urakan-tehtavat db urakka-id)))

(defn tallenna-toteuma-ja-yksikkohintaiset-tehtavat
  "Tallentaa toteuman ja palauttaa sen."
  [db user toteuma]
  (oik/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo} ; FIXME Oikea rooli?
                            (:urakka toteuma))
  (log/debug "Toteuman tallennus aloitettu. Payload: " (pr-str toteuma))
  (jdbc/with-db-transaction [c db]
                            (if (:toteuma-id toteuma)
                                            (do
                                              (log/info "Pävitetään toteuma.")
                                              (q/paivita-toteuma! c (konv/sql-date (:aloituspvm toteuma)) (konv/sql-date (:lopetuspvm toteuma)) (:id user)
                                                                  (:suorittajan-nimi toteuma) (:suorittajan-ytunnus toteuma) (:lisatieto toteuma) (:toteuma-id toteuma) (:urakka-id toteuma))
                                              (log/info "Käsitellään toteuman tehtävät: " (pr-str (:tehtavat toteuma)))
                                              (doseq [tehtava (:tehtavat toteuma)]
                                                    (if (and (:tehtava-id tehtava) (pos? (:tehtava-id tehtava)))
                                                      (do (log/info "Pävitetään tehtävä.")
                                                      (q/paivita-urakan-yk-hint-toteumien-tehtavat! c (:toimenpidekoodi tehtava) (:maara tehtava) (or (:poistettu tehtava) false) (:tehtava-id tehtava)))
                                                      (do (log/info "Luodaan uusi tehtävä.")
                                                      (q/luo-tehtava<! c (:toteuma-id toteuma) (:toimenpidekoodi tehtava) (:maara tehtava) (:id user)))))
                                              toteuma)
                                            (do
                                              (log/info "Luodaan uusi toteuma")
                                              (let [uusi (q/luo-toteuma<! c (:urakka-id toteuma) (:sopimus-id toteuma)
                                                                          (konv/sql-timestamp (:aloituspvm toteuma))
                                                                          (konv/sql-timestamp (:lopetuspvm toteuma))
                                                                          (name (:tyyppi toteuma))
                                                                          (:id user)
                                                                          (:suorittajan-nimi toteuma)
                                                                          (:suorittajan-ytunnus toteuma)
                                                                          (:lisatieto toteuma))
                                                    id (:id uusi)
                                                    toteumatyyppi (name (:tyyppi toteuma))]
                                                (log/info "Luodaan uudelle toteumalle tehtävät")
                                                (doseq [{:keys [toimenpidekoodi maara]} (:tehtavat toteuma)]
                                                  (q/luo-tehtava<! c id toimenpidekoodi maara (:id user))

                                                  (log/debug "Merkitään maksuera likaiseksi tyypin: " toteumatyyppi " toteumalle jonka toimenpidekoodi on: " toimenpidekoodi)
                                                  (q/merkitse-toteuman-maksuera-likaiseksi! c toteumatyyppi toimenpidekoodi))
                                                true)
                                              toteuma))))

(defn paivita-yk-hint-toiden-tehtavat
  "Päivittää yksikköhintaisen töiden toteutuneet tehtävät. Palauttaa urakan toteutuneet tehtävät ensimmäisen tehtävän toimenpidekoodilla.
  Lisäksi palauttaa urakan toteumat"
  [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi tehtavat]}]
  (oik/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo} urakka-id)
  (log/debug (str "Yksikköhintaisten töiden päivitys aloitettu. Payload: " (pr-str (into [] tehtavat))))

  (let [tehtavatidt (into #{} (map #(:tehtava_id %) tehtavat))]
    (jdbc/with-db-transaction [c db]
                              (doall
                                (for [tehtava tehtavat]
                                  (do
                                    (log/debug (str "Päivitetään saapunut tehtävä. id: " (:tehtava_id tehtava)))
                                    (q/paivita-urakan-yk-hint-toteumien-tehtavat! c (:toimenpidekoodi tehtava) (:maara tehtava) (:poistettu tehtava) (:tehtava_id tehtava)))))

                              (log/debug "Merkitään tehtavien: " tehtavatidt " maksuerät likaisiksi")
                              (q/merkitse-toteumatehtavien-maksuerat-likaisiksi! c tehtavatidt)))

  (let [paivitetyt-tehtavat (hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db user {:urakka-id urakka-id
                                                                      :sopimus-id sopimus-id
                                                                      :alkupvm alkupvm
                                                                      :loppupvm loppupvm
                                                                      :tyyppi tyyppi
                                                                      :toimenpidekoodi (:toimenpidekoodi (first tehtavat))})
        paivitetyt-toteumat (hae-urakan-toteumat db user {:urakka-id urakka-id
                                                         :sopimus-id sopimus-id
                                                         :alkupvm alkupvm
                                                         :loppupvm loppupvm
                                                         :tyyppi tyyppi})]
    (log/debug "Palautetaan päivittynyt data: " (pr-str paivitetyt-tehtavat))
    {:tehtavat paivitetyt-tehtavat :toteumat paivitetyt-toteumat}))

(def erilliskustannus-tyyppi-xf
  (map #(assoc % :tyyppi (keyword (:tyyppi %)))))

(def erilliskustannus-rahasumma-xf
  (map #(if (:rahasumma %)
         (assoc % :rahasumma (double (:rahasumma %)))
         (identity %))))

(def erilliskustannus-xf
  (comp
    erilliskustannus-tyyppi-xf
    erilliskustannus-rahasumma-xf))

(defn hae-urakan-erilliskustannukset [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan erilliskustannukset: " urakka-id " ajalta " alkupvm "-" loppupvm)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        erilliskustannus-xf
        (q/listaa-urakan-hoitokauden-erilliskustannukset db urakka-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))

(defn tallenna-erilliskustannus [db user ek]
  (oik/vaadi-rooli-urakassa user
                            #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo}
                            (:urakka-id ek))
  (jdbc/with-db-transaction [c db]
                            (if (not (:id ek))
                              (q/luo-erilliskustannus<! c (:tyyppi ek) (:sopimus ek) (:toimenpideinstanssi ek)
                                                        (konv/sql-date (:pvm ek)) (:rahasumma ek) (:indeksin_nimi ek) (:lisatieto ek) (:id user))

                              (q/paivita-erilliskustannus! c (:tyyppi ek) (:sopimus ek) (:toimenpideinstanssi ek)
                                                           (konv/sql-date (:pvm ek)) (:rahasumma ek) (:indeksin_nimi ek) (:lisatieto ek) (:id user)
                                                           (or (:poistettu ek) false) (:id ek)))

                            (log/debug "Merkitään kustannussuunnitelma likaiseksi erilliskustannuksen toimenpideinstanssille: " (:toimenpideinstanssi ek))
                            (q/merkitse-toimenpideinstanssin-kustannussuunnitelma-likaiseksi! c (:toimenpideinstanssi ek))

                            (hae-urakan-erilliskustannukset c user {:urakka-id (:urakka-id ek)
                                                                    :alkupvm   (:alkupvm ek)
                                                                    :loppupvm  (:loppupvm ek)})))

(defn tallenna-toteuma-ja-toteumamateriaalit
  "Tallentaa toteuman ja toteuma-materiaalin, ja palauttaa lopuksi kaikki urakassa käytetyt materiaalit (yksi rivi per materiaali).
  Tiedon mukana tulee yhteenlaskettu summa materiaalin käytöstä.
  * Jos tähän funktioon tehdään muutoksia, pitäisi muutokset tehdä myös
  materiaalit/tallenna-toteumamateriaaleja! funktioon (todnäk)"
  [db user t toteumamateriaalit hoitokausi sopimus]
  (oik/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo} ;fixme roolit??
                            (:urakka t))
  (log/info "Tallenna toteuma: " (pr-str t) " ja toteumamateriaalit " (pr-str toteumamateriaalit))
  (jdbc/with-db-transaction [c db]
                            ;; Jos toteumalla on positiivinen id, toteuma on olemassa
                            (let [toteuma (if (and (:id t) (pos? (:id t)))
                                            ;; Jos poistettu=true, halutaan toteuma poistaa.
                                            ;; Molemmissa tapauksissa parametrina saatu toteuma tulee palauttaa
                                            (if (:poistettu t)
                                              (do
                                                (log/info "Poistetaan toteuma " (:id t))
                                                (q/poista-toteuma! c (:id user) (:id t))
                                                t)
                                              (do
                                                (log/info "Pävitetään toteumaa " (:id t))
                                                (q/paivita-toteuma! c (konv/sql-date (:alkanut t)) (konv/sql-date (:paattynyt t)) (:id user)
                                                                    (:suorittajan-nimi t) (:suorittajan-ytunnus t) (:lisatieto t) (:id t) (:urakka t))
                                                t))
                                            ;; Jos id:tä ei ole tai se on negatiivinen, halutaan luoda uusi toteuma
                                            ;; Tässä tapauksessa palautetaan kyselyn luoma toteuma
                                            (do
                                              (log/info "Luodaan uusi toteuma")
                                              (q/luo-toteuma<!
                                                c (:urakka t) (:sopimus t) (konv/sql-date (:alkanut t))
                                                (konv/sql-date (:paattynyt t)) (:tyyppi t) (:id user)
                                                (:suorittajan-nimi t)
                                                (:suorittajan-ytunnus t)
                                                (:lisatieto t))))]
                              (log/info "Toteuman tallentamisen tulos:" (pr-str toteuma))

                              (doall
                                (for [tm toteumamateriaalit]
                                  ;; Positiivinen id = luodaan tai poistetaan toteuma-materiaali
                                  (if (and (:id tm) (pos? (:id tm)))
                                    (if (:poistettu tm)
                                      (do
                                        (log/info "Poistetaan materiaalitoteuma " (:id tm))
                                        (materiaalit-q/poista-toteuma-materiaali! c (:id user) (:id tm)))
                                      (do
                                        (log/info "Päivitä materiaalitoteuma "
                                                  (:id tm) " (" (:materiaalikoodi tm) ", " (:maara tm) ", " (:poistettu tm) "), toteumassa " (:id toteuma))
                                        (materiaalit-q/paivita-toteuma-materiaali!
                                          c (:materiaalikoodi tm) (:maara tm) (:id user) (:id toteuma) (:id tm))))
                                    (do
                                      (log/info "Luo uusi materiaalitoteuma (" (:materiaalikoodi tm) ", " (:maara tm) ") toteumalle " (:id toteuma))
                                      (materiaalit-q/luo-toteuma-materiaali<! c (:id toteuma) (:materiaalikoodi tm) (:maara tm) (:id user))))))
                              ;; Jos saatiin parametrina hoitokausi, voidaan palauttaa urakassa käytetyt materiaalit
                              ;; Tämä ei ole ehkä paras mahdollinen tapa hoitaa tätä, mutta toteuma/materiaalit näkymässä
                              ;; tarvitaan tätä tietoa. -Teemu K
                              (when hoitokausi
                                (materiaalipalvelut/hae-urakassa-kaytetyt-materiaalit c user (:urakka toteuma) (first hoitokausi) (second hoitokausi) sopimus)))))

(defn poista-toteuma!
  [db user t]
  (oik/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo} ;fixme roolit??
                            (:urakka t))
  (jdbc/with-db-transaction [c db]
                            (let [mat-ja-teht (q/hae-toteuman-toteuma-materiaalit-ja-tehtavat c (:id t))
                                  tehtavaidt (filterv #(not (nil? %)) (map :tehtava_id mat-ja-teht))]

                              (log/debug "Merkitään tehtavien: " tehtavaidt " maksuerät likaisiksi")
                              (q/merkitse-toteumatehtavien-maksuerat-likaisiksi! c tehtavaidt)

                              (materiaalit-q/poista-toteuma-materiaali!
                                c (:id user) (filterv #(not (nil? %)) (map :materiaali_id mat-ja-teht)))
                              (q/poista-tehtava! c (:id user) tehtavaidt)
                              (q/poista-toteuma! c (:id user) (:id t))
                              true)))

(defn poista-tehtava!
  "Poistaa toteuma-tehtävän id:llä. Vaatii lisäksi urakan id:n oikeuksien tarkastamiseen.
  {:urakka X, :id [A, B, ..]}"
  [db user tiedot]
  (oik/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo} ;fixme roolit??
                            (:urakka tiedot))
  (let [tehtavaid (:id tiedot)]
    (log/debug "Merkitään tehtava: " tehtavaid " maksuerä likaiseksi")
    (q/merkitse-toteumatehtavien-maksuerat-likaisiksi! db tehtavaid)

    (q/poista-tehtava! db (:id user) (:id tiedot))))

(defrecord Toteumat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-toteumat
                        (fn [user tiedot]
                          (hae-urakan-toteumat db user tiedot)))
      (julkaise-palvelu http :urakan-toteuma
                        (fn [user tiedot]
                          (hae-urakan-toteuma db user tiedot)))
      (julkaise-palvelu http :poista-toteuma!
                        (fn [user toteuma]
                          (poista-toteuma! db user toteuma)))
      (julkaise-palvelu http :poista-tehtava!
                        (fn [user tiedot]
                          (poista-tehtava! db user tiedot)))
      (julkaise-palvelu http :urakan-toteutuneet-tehtavat
                        (fn [user tiedot]
                          (hae-urakan-toteutuneet-tehtavat db user tiedot)))
      (julkaise-palvelu http :urakan-toteutuneet-tehtavat-toimenpidekoodilla
                        (fn [user tiedot]
                          (hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db user tiedot)))
      (julkaise-palvelu http :urakan-toteuma-paivat
                        (fn [user tiedot]
                          (hae-urakan-toteuma-paivat db user tiedot)))
      (julkaise-palvelu http :hae-urakan-tehtavat
                        (fn [user urakka-id]
                          (hae-urakan-tehtavat db user urakka-id)))
      (julkaise-palvelu http :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat
                        (fn [user toteuma]
                          (tallenna-toteuma-ja-yksikkohintaiset-tehtavat db user toteuma)))
      (julkaise-palvelu http :paivita-yk-hint-toteumien-tehtavat
                        (fn [user tiedot]
                          (paivita-yk-hint-toiden-tehtavat db user tiedot)))
      (julkaise-palvelu http :urakan-erilliskustannukset
                        (fn [user tiedot]
                          (hae-urakan-erilliskustannukset db user tiedot)))
      (julkaise-palvelu http :tallenna-erilliskustannus
                        (fn [user toteuma]
                          (tallenna-erilliskustannus db user toteuma)))
      (julkaise-palvelu http :tallenna-toteuma-ja-toteumamateriaalit
                        (fn [user tiedot]
                          (tallenna-toteuma-ja-toteumamateriaalit db user (:toteuma tiedot)
                                                                  (:toteumamateriaalit tiedot)
                                                                  (:hoitokausi tiedot)
                                                                  (:sopimus tiedot))))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-toteumat
      :urakan-toteuma-paivat
      :hae-urakan-tehtavat
      :tallenna-urakan-toteuma
      :urakan-erilliskustannukset
      :paivita-yk-hint-toteumien-tehtavat
      :tallenna-erilliskustannus
      :tallenna-toteuma-ja-toteumamateriaalit
      :poista-toteuma!
      :poista-tehtava!)
    this))
