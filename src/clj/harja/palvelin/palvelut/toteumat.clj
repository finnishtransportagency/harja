(ns harja.palvelin.palvelut.toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.palvelut.toteumat-tarkistukset :as tarkistukset]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]

            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.materiaalit :as materiaalit-q]
            [harja.kyselyt.muutoshintaiset-tyot :as mht-q]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.geometriapaivitykset :as geometriat-q]
            [harja.palvelin.palvelut.tierekisteri-haku :as tr-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]
            [harja.transit :as transit]
            [clojure.core.async :as async]
            [harja.pvm :as pvm]
            [harja.domain.tierekisteri.tietolajit :as tietolajit]
            [harja.tyokalut.functor :as functor]
            [harja.kyselyt.livitunnisteet :as livitunnisteet]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.palvelut.interpolointi :as interpolointi]))

(defn geometriaksi [reitti]
  (when reitti (geo/geometry (geo/clj->pg reitti))))

(def toteuma-xf
  (comp (map #(-> %
                  (konv/array->vec :tehtavat)
                  (konv/array->vec :materiaalit)))))

(def muunna-desimaaliluvut-xf
  (map #(-> %
            (assoc :maara
                   (or (some-> % :maara double) 0)))))

(defn vaadi-toteuma-ei-jarjestelman-luoma [db toteuma-id]
  (log/debug "Tarkistetaan, ettei toteuma " toteuma-id " ole järjestelmästä tullut")
  (when toteuma-id
    (let [jarjestelman-lisaama? (:jarjestelmanlisaama (first
                                                        (toteumat-q/toteuma-jarjestelman-lisaama
                                                          db {:toteuma toteuma-id})))]
      (when jarjestelman-lisaama?
        (throw (SecurityException. "Järjestelmän luomaa toteumaa ei voi muokata!"))))))

(defn vaadi-toteuma-kuuluu-urakkaan [db toteuma-id vaitetty-urakka-id]
  (log/debug "Tarkikistetaan, että toteuma " toteuma-id " kuuluu väitettyyn urakkaan " vaitetty-urakka-id)
  (assert vaitetty-urakka-id "Urakka id puuttuu!")
  (when toteuma-id
    (let [toteuman-todellinen-urakka-id (:urakka (first
                                                   (toteumat-q/toteuman-urakka
                                                     db {:toteuma toteuma-id})))]
      (when (and (some? toteuman-todellinen-urakka-id)
                 (not= toteuman-todellinen-urakka-id vaitetty-urakka-id))
        (throw (SecurityException. (str "Toteuma ei kuulu väitettyyn urakkaan " vaitetty-urakka-id
                                        " vaan urakkaan " toteuman-todellinen-urakka-id)))))))

(def tyhja-tr-osoite {:numero nil :alkuosa nil :alkuetaisyys nil :loppuosa nil :loppuetaisyys nil})

(defn toteuman-parametrit [toteuma kayttaja]
  (merge tyhja-tr-osoite
         (:tr toteuma)
         {:urakka (:urakka-id toteuma)
          :sopimus (:sopimus-id toteuma)
          :alkanut (konv/sql-timestamp (:alkanut toteuma))
          :paattynyt (konv/sql-timestamp (or (:paattynyt toteuma)
                                             (:alkanut toteuma)))
          :tyyppi (name (:tyyppi toteuma))
          :kayttaja (:id kayttaja)
          :suorittaja (:suorittajan-nimi toteuma)
          :ytunnus (:suorittajan-ytunnus toteuma)
          :lisatieto (:lisatieto toteuma)
          :ulkoinen_id nil
          :lahde "harja-ui"}))

(defn toteumatehtavan-parametrit [toteuma kayttaja]
  [(get-in toteuma [:tehtava :toimenpidekoodi]) (get-in toteuma [:tehtava :maara]) (:id kayttaja)
   (get-in toteuma [:tehtava :paivanhinta])])


(defn hae-urakan-toteuma [db user {:keys [urakka-id toteuma-id]}]
  (log/debug "Haetaan urakan toteuma id:llä: " toteuma-id)
  (let [toteuman-tyyppi (:tyyppi (first (toteumat-q/toteuman-tyyppi db toteuma-id)))
        _ (case toteuman-tyyppi
            "yksikkohintainen"
            (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-yksikkohintaisettyot   user urakka-id)
            "kokonaishintainen"
            (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot   user urakka-id))
        toteuma (konv/sarakkeet-vektoriin
                  (into []
                        (comp
                          toteuma-xf
                          (map konv/alaviiva->rakenne))
                        (toteumat-q/hae-urakan-toteuma db urakka-id toteuma-id))
                  {:tehtava :tehtavat} :id :tehtava-id)]
    (first toteuma)))

(defn hae-urakan-toteumien-tehtavien-summat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm
                                                             tyyppi toimenpide-id tehtava-id]}]
  (log/debug "Haetaan urakan toteuman tehtävien summat: " urakka-id sopimus-id alkupvm loppupvm tyyppi toimenpide-id tehtava-id)
  (oikeudet/vaadi-lukuoikeus (case tyyppi
                               :kokonaishintainen oikeudet/urakat-toteumat-kokonaishintaisettyot
                               :yksikkohintainen oikeudet/urakat-toteumat-yksikkohintaisettyot
                               (:akillinen-hoitotyo :lisatyo :muutostyo :vahinkojen-korjaukset) oikeudet/urakat-toteumat-muutos-ja-lisatyot
                               :materiaali oikeudet/urakat-toteumat-materiaalit

                               :default oikeudet/urakat-toteumat-kokonaishintaisettyot)
                             user urakka-id)
  (into []
        muunna-desimaaliluvut-xf
        (toteumat-q/hae-toteumien-tehtavien-summat
          db
          {:urakka urakka-id
           :sopimus sopimus-id
           :alkanut (konv/sql-date alkupvm)
           :paattynyt (konv/sql-date loppupvm)
           :tyyppi (name tyyppi)
           :toimenpide toimenpide-id
           :tehtava tehtava-id})))

(defn hae-urakan-toteutuneet-tehtavat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi]}]
  (log/debug "Haetaan urakan toteutuneet tehtävät: " urakka-id sopimus-id alkupvm loppupvm tyyppi)
  (oikeudet/vaadi-lukuoikeus (case tyyppi
                               :kokonaishintainen oikeudet/urakat-toteumat-kokonaishintaisettyot
                               :yksikkohintainen oikeudet/urakat-toteumat-yksikkohintaisettyot
                               (:akillinen-hoitotyo :lisatyo :muutostyo :vahinkojen-korjaukset) oikeudet/urakat-toteumat-muutos-ja-lisatyot
                               :materiaali oikeudet/urakat-toteumat-materiaalit

                               :default oikeudet/urakat-toteumat-kokonaishintaisettyot)
                             user urakka-id)
  (let [toteutuneet-tehtavat (into []
                                   muunna-desimaaliluvut-xf
                                   (toteumat-q/hae-urakan-ja-sopimuksen-toteutuneet-tehtavat
                                     db
                                     urakka-id
                                     sopimus-id
                                     (konv/sql-timestamp alkupvm)
                                     (konv/sql-timestamp loppupvm)
                                     (name tyyppi)))]
    (log/debug "Haetty urakan toteutuneet tehtävät: " toteutuneet-tehtavat)
    toteutuneet-tehtavat))

(defn hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi toimenpidekoodi]}]
  (log/debug "Haetaan urakan toteutuneet tehtävät tyypillä ja toimenpidekoodilla: " urakka-id sopimus-id alkupvm loppupvm tyyppi toimenpidekoodi)
  (oikeudet/vaadi-lukuoikeus (case tyyppi
                               :kokonaishintainen oikeudet/urakat-toteumat-kokonaishintaisettyot
                               :yksikkohintainen oikeudet/urakat-toteumat-yksikkohintaisettyot
                               (:akillinen-hoitotyo :lisatyo :muutostyo :vahinkojen-korjaukset) oikeudet/urakat-toteumat-muutos-ja-lisatyot
                               :materiaali oikeudet/urakat-toteumat-materiaalit

                               :default oikeudet/urakat-toteumat-kokonaishintaisettyot)
                             user urakka-id)
  (into []
        (comp (map konv/keraa-tr-kentat)
              muunna-desimaaliluvut-xf)
        (toteumat-q/hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla
          db
          urakka-id
          sopimus-id
          (konv/sql-timestamp alkupvm)
          (konv/sql-timestamp loppupvm)
          (name tyyppi)
          toimenpidekoodi)))

(defn hae-urakan-tehtavat [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (into []
        (toteumat-q/hae-urakan-tehtavat db urakka-id)))

(defn kasittele-toteumatehtava [c user toteuma tehtava]
  (if (and (:tehtava-id tehtava) (pos? (:tehtava-id tehtava)))
    (do
      (if (:poistettu tehtava)
        (do (log/debug "Poistetaan tehtävä: " (pr-str tehtava))
            (toteumat-q/poista-toteuman-tehtava! c (:tehtava-id tehtava)))
        (do (log/debug "Pävitetään tehtävä: " (pr-str tehtava))
            (toteumat-q/paivita-toteuman-tehtava! c (:toimenpidekoodi tehtava) (:maara tehtava) (or (:poistettu tehtava) false)
                                                  (or (:paivanhinta tehtava) nil)
                                                  (:tehtava-id tehtava)))))
    (do
      (when (not (:poistettu tehtava))
        (log/debug "Luodaan uusi tehtävä.")
        (toteumat-q/luo-tehtava<! c (:toteuma-id toteuma) (:toimenpidekoodi tehtava) (:maara tehtava) (:id user) nil)))))

(defn kasittele-toteuman-tehtavat [c user toteuma]
  (doseq [tehtava (:tehtavat toteuma)]
    (kasittele-toteumatehtava c user toteuma tehtava)))

(defn paivita-toteuman-reitti [db id reitti]
  (when reitti
    (toteumat-q/paivita-toteuman-reitti! db {:id id
                                             :reitti (geometriaksi reitti)})))

(defn paivita-toteuma [c user toteuma]
  (toteumat-q/paivita-toteuma! c (assoc (toteuman-parametrit toteuma user)
                                   :id (:toteuma-id toteuma)))
  (paivita-toteuman-reitti c (:toteuma-id toteuma) (:reitti toteuma))
  (kasittele-toteuman-tehtavat c user toteuma)
  (:toteuma-id toteuma))

(defn luo-toteuma [c user toteuma]
  (let [toteuman-parametrit (-> (toteuman-parametrit toteuma user) (assoc :reitti (geometriaksi (:reitti toteuma))))
        uusi (toteumat-q/luo-toteuma<! c toteuman-parametrit)
        id (:id uusi)
        toteumatyyppi (name (:tyyppi toteuma))]
    (if (and (= "kokonaishintainen" (:tyyppi toteuman-parametrit))
             (nil? (:reitti toteuman-parametrit)))
      {:virhe "Kokonaishintainen toteuma vaatii reitin. Reitti oli tyhjä."}

      (do
        (doseq [{:keys [toimenpidekoodi maara]} (:tehtavat toteuma)]
          (toteumat-q/luo-tehtava<! c id toimenpidekoodi maara (:id user) nil)
          (toteumat-q/merkitse-toteuman-maksuera-likaiseksi! c toteumatyyppi toimenpidekoodi))
        id))))

(defn hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
  [db user {:keys [urakka-id sopimus-id alkupvm loppupvm toimenpide tehtava]}]
  (log/debug "Aikaväli: " (pr-str alkupvm) (pr-str loppupvm))
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (let [toteumat (into []
                       (comp
                         (filter #(not (nil? (:toimenpidekoodi %))))
                         (map konv/alaviiva->rakenne))
                       (toteumat-q/hae-urakan-kokonaishintaiset-toteumat-paivakohtaisina-summina
                         db urakka-id
                         sopimus-id
                         (konv/sql-date alkupvm)
                         (konv/sql-date loppupvm)
                         toimenpide
                         tehtava))]
    toteumat))

(defn tallenna-toteuma-ja-yksikkohintaiset-tehtavat
  "Tallentaa toteuman. Palauttaa sen ja tehtävien summat."
  [db user toteuma]
  (oikeudet/vaadi-kirjoitusoikeus (case (:tyyppi toteuma)
                                    :kokonaishintainen oikeudet/urakat-toteumat-kokonaishintaisettyot
                                    :yksikkohintainen oikeudet/urakat-toteumat-yksikkohintaisettyot
                                    (:akillinen-hoitotyo :lisatyo :muutostyo :vahinkojen-korjaukset) oikeudet/urakat-toteumat-muutos-ja-lisatyot
                                    :materiaali oikeudet/urakat-toteumat-materiaalit

                                    :default oikeudet/urakat-toteumat-kokonaishintaisettyot)
                                  user (:urakka-id toteuma))
  (log/debug "Toteuman tallennus aloitettu. Payload: " (pr-str toteuma))
  (jdbc/with-db-transaction [c db]
    (tarkistukset/vaadi-toteuma-kuuluu-urakkaan c (:toteuma-id toteuma) (:urakka-id toteuma))
    (tarkistukset/vaadi-toteuma-ei-jarjestelman-luoma c (:toteuma-id toteuma))
    (let [id (if (:toteuma-id toteuma)
               (paivita-toteuma c user toteuma)
               (luo-toteuma c user toteuma))
          paivitetyt-summat (hae-urakan-toteumien-tehtavien-summat c user
                                                                   {:urakka-id (:urakka-id toteuma)
                                                                    :sopimus-id (:sopimus-id toteuma)
                                                                    :alkupvm (konv/sql-timestamp (:hoitokausi-aloituspvm toteuma))
                                                                    :loppupvm (konv/sql-timestamp (:hoitokausi-lopetuspvm toteuma))
                                                                    :toimenpide-id (:toimenpide-id toteuma)
                                                                    :tyyppi (:tyyppi toteuma)})]
      {:toteuma (assoc toteuma :toteuma-id id)
       :tehtavien-summat paivitetyt-summat})))

(defn tallenna-toteuma-ja-kokonaishintaiset-tehtavat
  "Tallentaa toteuman. Palauttaa sen ja tehtävien summat."
  [db user toteuma hakuparametrit]
  (oikeudet/vaadi-kirjoitusoikeus (case (:tyyppi toteuma)
                                    :kokonaishintainen oikeudet/urakat-toteumat-kokonaishintaisettyot
                                    :yksikkohintainen oikeudet/urakat-toteumat-yksikkohintaisettyot
                                    (:akillinen-hoitotyo :lisatyo :muutostyo :vahinkojen-korjaukset) oikeudet/urakat-toteumat-muutos-ja-lisatyot
                                    :materiaali oikeudet/urakat-toteumat-materiaalit

                                    :default oikeudet/urakat-toteumat-kokonaishintaisettyot)
                                  user (:urakka-id toteuma))
  (log/debug "Toteuman tallennus aloitettu. Payload: " (pr-str toteuma))
  (jdbc/with-db-transaction
    [db db]
    (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db (:toteuma-id toteuma) (:urakka-id toteuma))
    (tarkistukset/vaadi-toteuma-ei-jarjestelman-luoma db (:toteuma-id toteuma))
    (let [tulos (if (:toteuma-id toteuma)
                  (paivita-toteuma db user toteuma)
                  (luo-toteuma db user toteuma))]

      (if-not (:virhe tulos)
        (hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
          db user hakuparametrit)

        tulos))))

(defn paivita-yk-hint-toiden-tehtavat
  "Päivittää yksikköhintaisen töiden toteutuneet tehtävät. Palauttaa päivitetyt tehtävät sekä tehtävien summat"
  [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi tehtavat toimenpide-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-yksikkohintaisettyot user urakka-id)
  (log/debug (str "Yksikköhintaisten töiden päivitys aloitettu. Payload: " (pr-str (into [] tehtavat))))

  (let [tehtavatidt (into #{} (map #(:tehtava_id %) tehtavat))]
    (jdbc/with-db-transaction [c db]
      (doseq [tehtava tehtavat]
        (let [toteuma-id (:toteuma (first (toteumat-q/tehtavan-toteuma db (:tehtava_id tehtava))))]
          (tarkistukset/vaadi-toteuma-kuuluu-urakkaan c toteuma-id urakka-id)
          (tarkistukset/vaadi-toteuma-ei-jarjestelman-luoma c toteuma-id)
          (log/debug (str "Päivitetään saapunut tehtävä. id: " (:tehtava_id tehtava)))
          (toteumat-q/paivita-toteuman-tehtava! c (:toimenpidekoodi tehtava) (or (:maara tehtava) 0) (:poistettu tehtava)
                                                (:paivanhinta tehtava) (:tehtava_id tehtava)))

        (log/debug "Merkitään tehtavien: " tehtavatidt " maksuerät likaisiksi")
        (toteumat-q/merkitse-toteumatehtavien-maksuerat-likaisiksi! c tehtavatidt))))

  (let [paivitetyt-tehtavat (hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db user
                                                                                {:urakka-id urakka-id
                                                                                 :sopimus-id sopimus-id
                                                                                 :alkupvm alkupvm
                                                                                 :loppupvm loppupvm
                                                                                 :tyyppi tyyppi
                                                                                 :toimenpidekoodi (:toimenpidekoodi (first tehtavat))})
        paivitetyt-summat (hae-urakan-toteumien-tehtavien-summat db user
                                                                 {:urakka-id urakka-id
                                                                  :sopimus-id sopimus-id
                                                                  :alkupvm alkupvm
                                                                  :loppupvm loppupvm
                                                                  :toimenpide-id toimenpide-id
                                                                  :tyyppi tyyppi})]
    (log/debug "Palautetaan päivitetyt tehtävät " (pr-str paivitetyt-tehtavat) " ja summat " (pr-str paivitetyt-summat))
    {:tehtavat paivitetyt-tehtavat :tehtavien-summat paivitetyt-summat}))

(def erilliskustannus-tyyppi-xf
  (map #(assoc % :tyyppi (keyword (:tyyppi %)))))

(def erilliskustannus-rahasumma-xf
  (map #(if (:rahasumma %)
          (assoc % :rahasumma (double (:rahasumma %)))
          (identity %))))

(def erilliskustannus-xf
  (comp
    erilliskustannus-tyyppi-xf
    erilliskustannus-rahasumma-xf
    ;; Asiakastyytyväisyysbonuksen indeksikorotus lasketaan eri kaavalla
    (map #(if (= (:tyyppi %) :asiakastyytyvaisyysbonus)
            (assoc % :indeksikorjattuna (:bonus-indeksikorjattuna %))
            %))))

(defn hae-urakan-erilliskustannukset [db user {:keys [urakka-id alkupvm loppupvm]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-erilliskustannukset user urakka-id)
  (into []
        erilliskustannus-xf
        (toteumat-q/listaa-urakan-hoitokauden-erilliskustannukset db urakka-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))

(defn tallenna-erilliskustannus [db user ek]
  (log/debug "tallenna erilliskustannus:" ek)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-erilliskustannukset user (:urakka-id ek))
  (jdbc/with-db-transaction
    [db db]
    (let [parametrit [db (:tyyppi ek) (:urakka-id ek) (:sopimus ek) (:toimenpideinstanssi ek)
                      (konv/sql-date (:pvm ek)) (:rahasumma ek) (:indeksin_nimi ek) (:lisatieto ek) (:id user)]]
      (if (not (:id ek))
        (apply toteumat-q/luo-erilliskustannus<! parametrit)
        (apply toteumat-q/paivita-erilliskustannus! (concat parametrit [(or (:poistettu ek) false)
                                                                        (:id ek)
                                                                        (:urakka-id ek)])))
      (toteumat-q/merkitse-toimenpideinstanssin-kustannussuunnitelma-likaiseksi! db (:toimenpideinstanssi ek))
      (hae-urakan-erilliskustannukset db user {:urakka-id (:urakka-id ek)
                                               :alkupvm (:alkupvm ek)
                                               :loppupvm (:loppupvm ek)}))))


(def muut-tyot-rahasumma-xf
  (map #(if (:tehtava_paivanhinta %)
          (assoc % :tehtava_paivanhinta (double (:tehtava_paivanhinta %)))
          (identity %))))

(def muut-tyot-tyyppi-xf
  (map #(if (:tyyppi %)
          (assoc % :tyyppi (keyword (:tyyppi %)))
          (identity %))))

(def muut-tyot-maara-xf
  (map #(if (:tehtava_maara %)
          (assoc % :tehtava_maara (double (:tehtava_maara %)))
          (identity %))))


(def muut-tyot-xf
  (comp
    (harja.geo/muunna-pg-tulokset :reitti)
    muut-tyot-rahasumma-xf
    muut-tyot-maara-xf
    (map konv/alaviiva->rakenne)
    muut-tyot-tyyppi-xf))

(defn hae-urakan-muut-tyot [db user {:keys [urakka-id sopimus-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan muut työt: " urakka-id " ajalta " alkupvm "-" loppupvm)

  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-muutos-ja-lisatyot user urakka-id)
  (into []
        muut-tyot-xf
        (toteumat-q/listaa-urakan-hoitokauden-toteumat-muut-tyot db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))

(defn paivita-muun-tyon-toteuma
  [c user toteuma]
  (log/debug "Päivitä toteuma" toteuma)
  (if (:poistettu toteuma)
    (let [params [c (:id user) (get-in toteuma [:toteuma :id])]]
      (log/debug "poista toteuma" (get-in toteuma [:toteuma :id]))
      (apply toteumat-q/poista-toteuman-tehtavat! params)
      (apply toteumat-q/poista-toteuma! params))
    (do
      (toteumat-q/paivita-toteuma! c {:alkanut (konv/sql-date (:alkanut toteuma))
                                      :paattynyt (konv/sql-date (:paattynyt toteuma))
                                      :tyyppi (name (:tyyppi toteuma))
                                      :kayttaja (:id user)
                                      :suorittaja (:suorittajan-nimi toteuma)
                                      :ytunnus (:suorittajan-ytunnus toteuma)
                                      :lisatieto (:lisatieto toteuma)
                                      :numero (get-in toteuma [:tr :numero])
                                      :alkuosa (get-in toteuma [:tr :alkuosa])
                                      :alkuetaisyys (get-in toteuma [:tr :alkuetaisyys])
                                      :loppuosa (get-in toteuma [:tr :loppuosa])
                                      :loppuetaisyys (get-in toteuma [:tr :loppuetaisyys])
                                      :id (get-in toteuma [:toteuma :id])
                                      :urakka (:urakka-id toteuma)})
      (paivita-toteuman-reitti c (get-in toteuma [:toteuma :id]) (:reitti toteuma))
      (kasittele-toteumatehtava c user toteuma (assoc (:tehtava toteuma)
                                                 :tehtava-id (get-in toteuma [:tehtava :id]))))))

(defn luo-muun-tyon-toteuma
  [c user toteuma]
  (log/debug "Luodaan uusi toteuma" toteuma)
  (let [toteuman-parametrit (-> (toteuman-parametrit toteuma user) (assoc :reitti (geometriaksi (:reitti toteuma))))
        uusi (toteumat-q/luo-toteuma<! c toteuman-parametrit)
        id (:id uusi)
        toteumatyyppi (name (:tyyppi toteuma))
        maksueratyyppi (case toteumatyyppi
                         "muutostyo" "muu"
                         "akillinen-hoitotyo" "akillinen-hoitotyo"
                         "lisatyo" "lisatyo"
                         "muu")
        toteumatehtavan-parametrit
        (into [] (concat [c id] (toteumatehtavan-parametrit toteuma user)))
        {:keys [toimenpidekoodi]} (:tehtava toteuma)]
    (log/debug (str "Luodaan uudelle toteumalle id " id " tehtävä" toteumatehtavan-parametrit))
    (apply toteumat-q/luo-tehtava<! toteumatehtavan-parametrit)
    (log/debug "Merkitään maksuera likaiseksi maksuerätyypin: " maksueratyyppi " toteumalle jonka toimenpidekoodi on: " toimenpidekoodi)
    (toteumat-q/merkitse-toteuman-maksuera-likaiseksi! c maksueratyyppi toimenpidekoodi)
    true))

(defn tallenna-muiden-toiden-toteuma
  [db user toteuma]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-muutos-ja-lisatyot user (:urakka-id toteuma))
  (jdbc/with-db-transaction [db db]
    (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db (get-in toteuma [:toteuma :id]) (:urakka-id toteuma))
    (tarkistukset/vaadi-toteuma-ei-jarjestelman-luoma db (get-in toteuma [:toteuma :id]))
    (if (get-in toteuma [:toteuma :id])
      (paivita-muun-tyon-toteuma db user toteuma)
      (luo-muun-tyon-toteuma db user toteuma))
    ;; lisätään tarvittaessa hinta muutoshintainen_tyo tauluun
    (when (:uusi-muutoshintainen-tyo toteuma)
      (let [parametrit [db (:yksikko toteuma) (:yksikkohinta toteuma) (:id user)
                        (:urakka-id toteuma) (:sopimus-id toteuma) (get-in toteuma [:tehtava :toimenpidekoodi])
                        (konv/sql-date (:urakan-alkupvm toteuma))
                        (konv/sql-date (:urakan-loppupvm toteuma))]]
        (apply mht-q/lisaa-muutoshintainen-tyo<! parametrit)))
    (hae-urakan-muut-tyot db user
                          {:urakka-id (:urakka-id toteuma)
                           :sopimus-id (:sopimus-id toteuma)
                           :alkupvm (konv/sql-timestamp (:hoitokausi-aloituspvm toteuma))
                           :loppupvm (konv/sql-timestamp (:hoitokausi-lopetuspvm toteuma))})))



(defn tallenna-toteuma-ja-toteumamateriaalit
  "Tallentaa toteuman ja toteuma-materiaalin, ja palauttaa lopuksi kaikki urakassa käytetyt materiaalit
  (yksi rivi per materiaali).
  Tiedon mukana tulee yhteenlaskettu summa materiaalin käytöstä.
  * Jos tähän funktioon tehdään muutoksia, pitäisi muutokset tehdä myös
  materiaalit/tallenna-toteumamateriaaleja! funktioon (todnäk)"
  [db user t toteumamateriaalit hoitokausi sopimus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-materiaalit user (:urakka t))
  (log/debug "Tallenna toteuma: " (pr-str t) " ja toteumamateriaalit " (pr-str toteumamateriaalit))
  (jdbc/with-db-transaction [c db]
    (tarkistukset/vaadi-toteuma-kuuluu-urakkaan c (:id t) (:urakka t))
    (tarkistukset/vaadi-toteuma-ei-jarjestelman-luoma c (:id t))
    ;; Jos toteumalla on positiivinen id, toteuma on olemassa
    (let [toteuma (if (and (:id t) (pos? (:id t)))
                    ;; Jos poistettu=true, halutaan toteuma poistaa.
                    ;; Molemmissa tapauksissa parametrina saatu toteuma tulee palauttaa
                    (if (:poistettu t)
                      (do
                        (log/debug "Poistetaan toteuma " (:id t))
                        (toteumat-q/poista-toteuma! c (:id user) (:id t))
                        t)
                      (do
                        (log/debug "Pävitetään toteumaa " (:id t))
                        (toteumat-q/paivita-toteuma! c {:alkanut (konv/sql-date (:alkanut t))
                                                        :paattynyt (konv/sql-date (:paattynyt t))
                                                        :tyyppi (:tyyppi t)
                                                        :kayttaja (:id user)
                                                        :suorittaja (:suorittajan-nimi t)
                                                        :ytunnus (:suorittajan-ytunnus t)
                                                        :lisatieto (:lisatieto t)
                                                        :numero nil
                                                        :alkuosa nil
                                                        :alkuetaisyys nil
                                                        :loppuosa nil
                                                        :loppuetaisyys nil
                                                        :id (:id t)
                                                        :urakka (:urakka t)})
                        t))
                    ;; Jos id:tä ei ole tai se on negatiivinen, halutaan luoda uusi toteuma
                    ;; Tässä tapauksessa palautetaan kyselyn luoma toteuma
                    (do
                      (log/debug "Luodaan uusi toteuma")
                      (toteumat-q/luo-toteuma<!
                        c (:urakka t) (:sopimus t) (konv/sql-date (:alkanut t))
                        (konv/sql-date (:paattynyt t)) (:tyyppi t) (:id user)
                        (:suorittajan-nimi t)
                        (:suorittajan-ytunnus t)
                        (:lisatieto t)
                        nil
                        nil nil nil nil nil nil
                        "harja-ui")))]
      (log/debug "Toteuman tallentamisen tulos:" (pr-str toteuma))

      (doseq [tm toteumamateriaalit]
        ;; Positiivinen id = luodaan tai poistetaan toteuma-materiaali
        (if (and (:id tm) (pos? (:id tm)))
          (if (:poistettu tm)
            (do
              (log/debug "Poistetaan materiaalitoteuma " (:id tm))
              (materiaalit-q/poista-toteuma-materiaali! c (:id user) (:id tm)))
            (do
              (log/debug "Päivitä materiaalitoteuma "
                         (:id tm) " (" (:materiaalikoodi tm) ", " (:maara tm)
                         ", " (:poistettu tm) "), toteumassa " (:id toteuma))
              (materiaalit-q/paivita-toteuma-materiaali!
                c (:materiaalikoodi tm) (:maara tm) (:id user) (:id toteuma) (:id tm))))
          (do
            (log/debug "Luo uusi materiaalitoteuma (" (:materiaalikoodi tm)
                       ", " (:maara tm) ") toteumalle " (:id toteuma))
            (materiaalit-q/luo-toteuma-materiaali<! c (:id toteuma) (:materiaalikoodi tm)
                                                    (:maara tm) (:id user)))))

      (materiaalit-q/paivita-koko-sopimuksen-materiaalin-kaytto c (:sopimus t))

      ;; Jos saatiin parametrina hoitokausi, voidaan palauttaa urakassa käytetyt materiaalit
      ;; Tämä ei ole ehkä paras mahdollinen tapa hoitaa tätä, mutta toteuma/materiaalit näkymässä
      ;; tarvitaan tätä tietoa. -Teemu K
      (when hoitokausi
        (materiaalipalvelut/hae-urakassa-kaytetyt-materiaalit c user (:urakka toteuma)
                                                              (first hoitokausi) (second hoitokausi)
                                                              sopimus)))))

(defn poista-toteuma!
  [db user t]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-materiaalit user (:urakka t))
  (jdbc/with-db-transaction [c db]
    (let [mat-ja-teht (toteumat-q/hae-toteuman-toteuma-materiaalit-ja-tehtavat c (:id t))
          tehtavaidt (filterv #(not (nil? %)) (map :tehtava_id mat-ja-teht))]

      (log/debug "Merkitään tehtavien: " tehtavaidt " maksuerät likaisiksi")
      (toteumat-q/merkitse-toteumatehtavien-maksuerat-likaisiksi! c tehtavaidt)

      (materiaalit-q/poista-toteuma-materiaali!
        c (:id user) (filterv #(not (nil? %)) (map :materiaali_id mat-ja-teht)))
      (toteumat-q/poista-tehtava! c (:id user) tehtavaidt)
      (toteumat-q/poista-toteuma! c (:id user) (:id t))
      true)))

(defn poista-tehtava!
  "Poistaa toteuma-tehtävän id:llä. Vaatii lisäksi urakan id:n oikeuksien tarkastamiseen.
  {:urakka X, :id [A, B, ..]}"
  [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-yksikkohintaisettyot user (:urakka tiedot))
  (let [tehtavaid (:id tiedot)]
    (log/debug "Merkitään tehtava: " tehtavaid " maksuerä likaiseksi")
    (toteumat-q/merkitse-toteumatehtavien-maksuerat-likaisiksi! db tehtavaid)

    (toteumat-q/poista-tehtava! db (:id user) (:id tiedot))))

(defn varustetoteuma-xf
  "Palauttaa transducerin tietokannasta haettavien varustetoteumien muuntamiseen.
  Tierekisteri tarvitaan parametrina muuntamaan varusteiden arvot. "
  ([] (varustetoteuma-xf nil))
  ([tierekisteri]
   (comp
    (map #(assoc % :tyyppi-kartalla :varustetoteuma))
    (map #(konv/string->keyword % :toimenpide))
    (map #(konv/string->keyword % :toteumatyyppi))
    (harja.geo/muunna-pg-tulokset :reittipiste_sijainti)
    (if (nil? tierekisteri)
      (map identity)
      (map #(assoc % :arvot (tietolajit/validoi-ja-muunna-merkkijono-arvoiksi
                             tierekisteri
                             (:arvot %)
                             (:tietolaji %)))))
    (map konv/alaviiva->rakenne))))

(defn hae-urakan-varustetoteumat [tierekisteri db user {:keys [urakka-id sopimus-id alkupvm loppupvm tienumero] :as hakuehdot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (log/debug "Haetaan varustetoteumat: " urakka-id sopimus-id alkupvm loppupvm tienumero)
  (let [toteumat (into []
                       (varustetoteuma-xf tierekisteri)
                       (toteumat-q/hae-urakan-varustetoteumat
                         db
                         urakka-id
                         sopimus-id
                         (konv/sql-date alkupvm)
                         (konv/sql-date loppupvm)
                         (boolean tienumero)
                         tienumero))
        toteumat (konv/sarakkeet-vektoriin
                   toteumat
                   {:reittipiste :reittipisteet
                    :toteumatehtava :toteumatehtavat}
                   :id)]
    (log/debug "Palautetaan " (count toteumat) " varustetoteuma(a)")
    (map
      #(let [liitteet (toteumat-q/hae-toteuman-liitteet db (:toteumaid %))]
         (println "--->> liitteet" liitteet)
         (assoc % :liitteet liitteet))
      toteumat)))

(defn hae-kokonaishintaisen-toteuman-tiedot
  ([db user urakka-id pvm toimenpidekoodi]
   (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
   (into []
         (map konv/alaviiva->rakenne)
         (toteumat-q/hae-kokonaishintaisen-toteuman-tiedot
           db {:urakka urakka-id
               :pvm pvm
               :toimenpidekoodi toimenpidekoodi
               :toteuma nil})))
  ([db user urakka-id toteuma-id]
   (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
   (into []
         (map konv/alaviiva->rakenne)
         (toteumat-q/hae-kokonaishintaisen-toteuman-tiedot
           db {:urakka urakka-id
               :toteuma toteuma-id
               :pvm nil
               :toimenpidekoodi nil}))))

(defn tallenna-varustetoteuma [tierekisteri db user
                               hakuehdot
                               {:keys [id
                                       urakka-id
                                       arvot
                                       sijainti
                                       puoli
                                       ajorata
                                       tierekisteriosoite
                                       lisatieto
                                       tietolaji
                                       toiminto
                                       alkupvm
                                       loppupvm
                                       kuntoluokitus
                                       liitteet] :as toteuma}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (log/debug "Tallennetaan uusi varustetoteuma")
  (let [varustetoteuma-id
        (jdbc/with-db-transaction [db db]
          (let [nyt (pvm/nyt)
                sijainti
                (cond
                  (not (nil? sijainti)) (geo/geometry (geo/clj->pg sijainti))
                  (not (nil? tierekisteriosoite)) (geo/geometry (geo/clj->pg (tr-q/hae-tr-viiva db tierekisteriosoite)))
                  :else nil)
                toiminto (name toiminto)
                tunniste (if (= toiminto "lisatty")
                           (livitunnisteet/hae-seuraava-livitunniste db)
                           (:tunniste arvot))
                arvot (functor/fmap str (assoc arvot :tunniste tunniste))
                arvot (tietolajit/validoi-ja-muunna-arvot-merkkijonoksi tierekisteri arvot tietolaji)

                elynro (:elynumero (first (urakat-q/hae-urakan-ely db urakka-id)))
                sopimus-id (:id (first (sopimukset-q/hae-urakan-paasopimus db urakka-id)))
                karttapvm (or (geometriat-q/hae-karttapvm db) nyt)
                toteuma-id (:id (toteumat-q/luo-toteuma<!
                                  db
                                  urakka-id
                                  sopimus-id
                                  nyt
                                  nyt
                                  "kokonaishintainen"
                                  (:id user)
                                  (str (:etunimi user) " " (:sukunimi user))
                                  (get-in user [:organisaatio :ytunnus])
                                  lisatieto
                                  nil
                                  sijainti
                                  nil nil nil nil nil
                                  "harja-ui"))
                varustetoteuma {:id id
                                :tunniste tunniste
                                :toteuma toteuma-id
                                :toimenpide toiminto
                                :tietolaji tietolaji
                                :arvot arvot
                                :karttapvm karttapvm
                                :alkupvm alkupvm
                                :loppupvm loppupvm
                                :piiri elynro
                                :kuntoluokka kuntoluokitus
                                :tierekisteriurakkakoodi (:tierekisteriurakkakoodi arvot)
                                :luoja (:id user)
                                :tr_numero (:numero tierekisteriosoite)
                                :tr_alkuosa (:alkuosa tierekisteriosoite)
                                :tr_alkuetaisyys (:alkuetaisyys tierekisteriosoite)
                                :tr_loppuosa (:loppuosa tierekisteriosoite)
                                :tr_loppuetaisyys (:loppuetaisyys tierekisteriosoite)
                                :tr_puoli puoli
                                :tr_ajorata ajorata
                                :sijainti sijainti}
                id (if id
                     (toteumat-q/paivita-varustetoteuma! db varustetoteuma)
                     (:id (toteumat-q/luo-varustetoteuma<! db varustetoteuma)))]
            (when liitteet
              (doseq [liite liitteet]
                (toteumat-q/tallenna-liite-toteumalle<! db toteuma-id (:id liite))))
            id))]

    (async/thread (tierekisteri/laheta-varustetoteuma tierekisteri varustetoteuma-id)))

  (hae-urakan-varustetoteumat tierekisteri db user hakuehdot))

(defn hae-toteuman-reitti-ja-tr-osoite [db user {:keys [id urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (first
    (into []
          (comp
            (harja.geo/muunna-pg-tulokset :reitti)
            (map konv/alaviiva->rakenne))
          (toteumat-q/hae-toteuman-reitti-ja-tr-osoite db id))))

(defn- hae-toteumareitit-kartalle [db user extent p kysely-fn]
  (let [[x1 y1 x2 y2] extent
        alue {:xmin x1 :ymin y1
              :xmax x2 :ymax y2}
        toleranssi (geo/karkeistustoleranssi alue)
        kartalle-xf (esitettavat-asiat/kartalla-esitettavaan-muotoon-xf)

        ch (async/chan 32 (comp
                            (map konv/alaviiva->rakenne)
                            (map #(assoc % :tyyppi-kartalla :toteuma
                                           :tehtavat [(:tehtava %)]))
                            kartalle-xf))]
    (async/thread
      (try
        (jdbc/with-db-connection [db db
                                  {:read-only? true}]
                                 (kysely-fn db ch
                                            (merge p
                                                   alue
                                                   {:toleranssi toleranssi})))
        (catch Throwable t
          (log/warn t "Toteumareittien haku epäonnistui"))))
    ch))

(defn- hae-kokonaishintainen-toteuma-kartalle [db user {:keys [extent parametrit]}]
  (let [{urakka-id :urakka-id :as p} parametrit
        _ (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)]
    (hae-toteumareitit-kartalle db user extent p toteumat-q/hae-kokonaishintaisten-toiden-reitit)))

(defn- hae-kokonaishintaisen-toteuman-tiedot-kartalle [db user {:keys [x y] :as parametrit}]

  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user
                             (:urakka-id parametrit))
  (konv/sarakkeet-vektoriin
    (into []
          (comp (map #(assoc % :tyyppi-kartalla :toteuma))
                (map konv/alaviiva->rakenne)
                (map #(update % :tierekisteriosoite konv/lue-tr-osoite))
                (map #(interpolointi/interpoloi-toteuman-aika-pisteelle % parametrit db)))
          (toteumat-q/hae-toteumien-tiedot-pisteessa
            db
            (merge {:x x :y y :tyyppi "kokonaishintainen"}
                   parametrit)))
    {:tehtava :tehtavat
     :materiaalitoteuma :materiaalit}))

(defn- hae-yksikkohintaiset-toteumat-kartalle [db user {:keys [extent parametrit]}]
  (let [{urakka-id :urakka-id :as p} parametrit
        _ (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-yksikkohintaisettyot
                                     user urakka-id)]
    (hae-toteumareitit-kartalle db user extent p toteumat-q/hae-yksikkohintaisten-toiden-reitit)))

(defn- hae-yksikkohintaisen-toteuman-tiedot-kartalle [db user {:keys [x y] :as parametrit}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-yksikkohintaisettyot user
                             (:urakka-id parametrit))
  (konv/sarakkeet-vektoriin
    (into []
          (comp (map #(assoc % :tyyppi-kartalla :toteuma))
                (map konv/alaviiva->rakenne)
                (map #(update % :tierekisteriosoite konv/lue-tr-osoite))
                (map #(interpolointi/interpoloi-toteuman-aika-pisteelle % parametrit db)))
          (toteumat-q/hae-toteumien-tiedot-pisteessa
            db
            (merge {:x x :y y :tyyppi "yksikkohintainen"
                    :toimenpidekoodi nil}
                   parametrit)))
    {:tehtava :tehtavat
     :materiaalitoteuma :materiaalit}))

(defn- siirry-toteuma
  "Palauttaa frontin tarvitsemat tiedot, joilla toteumaan voidaan siirtyä"
  [db user toteuma-id]
  (first
    (konv/sarakkeet-vektoriin
      (into []
            (map konv/alaviiva->rakenne)
            (toteumat-q/siirry-toteuma
              db {:toteuma-id toteuma-id
                  :tarkista-urakka? (= :urakoitsija (roolit/osapuoli user))
                  :urakoitsija-id (get-in user [:organisaatio :id])}))
      {:tehtava :tehtavat} :id (constantly true))))

(defrecord Toteumat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           karttakuvat :karttakuvat
           tierekisteri :tierekisteri
           :as this}]
    (when karttakuvat
      (karttakuvat/rekisteroi-karttakuvan-lahde!
        karttakuvat :kokonaishintainen-toteuma
        (partial #'hae-kokonaishintainen-toteuma-kartalle db)
        (partial #'hae-kokonaishintaisen-toteuman-tiedot-kartalle db)
        "kht")
      (karttakuvat/rekisteroi-karttakuvan-lahde!
        karttakuvat :yksikkohintaiset-toteumat
        (partial #'hae-yksikkohintaiset-toteumat-kartalle db)
        (partial #'hae-yksikkohintaisen-toteuman-tiedot-kartalle db)
        "yht"))

    (julkaise-palvelut
      http
      :urakan-toteuma
      (fn [user tiedot]
        (hae-urakan-toteuma db user tiedot))
      :urakan-toteumien-tehtavien-summat
      (fn [user tiedot]
        (hae-urakan-toteumien-tehtavien-summat db user tiedot))
      :urakan-toteutuneet-tehtavat-toimenpidekoodilla
      (fn [user tiedot]
        (hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db user tiedot))
      :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat
      (fn [user toteuma]
        (tallenna-toteuma-ja-yksikkohintaiset-tehtavat db user toteuma))
      :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
      (fn [user {:keys [toteuma hakuparametrit]}]
        (tallenna-toteuma-ja-kokonaishintaiset-tehtavat db user toteuma hakuparametrit))
      :paivita-yk-hint-toteumien-tehtavat
      (fn [user tiedot]
        (paivita-yk-hint-toiden-tehtavat db user tiedot))
      :urakan-erilliskustannukset
      (fn [user tiedot]
        (hae-urakan-erilliskustannukset db user tiedot))
      :tallenna-erilliskustannus
      (fn [user toteuma]
        (tallenna-erilliskustannus db user toteuma))
      :urakan-muut-tyot
      (fn [user tiedot]
        (hae-urakan-muut-tyot db user tiedot))
      :tallenna-muiden-toiden-toteuma
      (fn [user toteuma]
        (tallenna-muiden-toiden-toteuma db user toteuma))
      :tallenna-toteuma-ja-toteumamateriaalit
      (fn [user tiedot]
        (tallenna-toteuma-ja-toteumamateriaalit db user (:toteuma tiedot)
                                                (:toteumamateriaalit tiedot)
                                                (:hoitokausi tiedot)
                                                (:sopimus tiedot)))
      :hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
      (fn [user tiedot]
        (hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat db user tiedot))
      :hae-kokonaishintaisen-toteuman-tiedot
      (fn [user {:keys [urakka-id pvm toimenpidekoodi toteuma-id]}]
        (if toteuma-id
          (hae-kokonaishintaisen-toteuman-tiedot db user urakka-id toteuma-id)
          (hae-kokonaishintaisen-toteuman-tiedot db user urakka-id pvm toimenpidekoodi)))
      :urakan-varustetoteumat
      (fn [user tiedot]
        (hae-urakan-varustetoteumat tierekisteri db user tiedot))
      :tallenna-varustetoteuma
      (fn [user {:keys [hakuehdot toteuma]}]
        (tallenna-varustetoteuma tierekisteri db user hakuehdot toteuma))
      :hae-toteuman-reitti-ja-tr-osoite
      (fn [user tiedot]
        (hae-toteuman-reitti-ja-tr-osoite db user tiedot))
      :siirry-toteuma
      (fn [user toteuma-id]
        (siirry-toteuma db user toteuma-id)))
    this)

  (stop [this]

    (poista-palvelut
      (:http-palvelin this)
      :urakan-toteuma
      :urakan-toteumien-tehtavien-summat
      :urakan-toteutuneet-tehtavat-toimenpidekoodilla
      :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat
      :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
      :paivita-yk-hint-toteumien-tehtavat
      :urakan-erilliskustannukset
      :tallenna-erilliskustannus
      :urakan-muut-tyot
      :tallenna-muiden-toiden-toteuma
      :tallenna-toteuma-ja-toteumamateriaalit
      :hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
      :hae-kokonaishintaisen-toteuman-tiedot
      :urakan-varustetoteumat
      :hae-toteuman-reitti-ja-tr-osoite
      :siirry-toteuma
      :tallenna-varustetoteuma)
    this))
