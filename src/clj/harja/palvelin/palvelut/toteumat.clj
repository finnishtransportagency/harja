(ns harja.palvelin.palvelut.toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.palvelut.toteumat-tarkistukset :as tarkistukset]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [slingshot.slingshot :refer [throw+]]

            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.materiaalit :as materiaalit-q]
            [harja.kyselyt.muutoshintaiset-tyot :as mht-q]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat-kyselyt]
            [harja.kyselyt.erilliskustannus-kyselyt :as erilliskustannus-kyselyt]
            [harja.palvelin.palvelut.tierekisteri-haku :as tr-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.toteuma :as toteuma]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [clojure.core.async :as async]
            [harja.id :as id]
            [harja.pvm :as pvm]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.palvelut.interpolointi :as interpolointi]
            [specql.core :refer [fetch update! upsert!]]))

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

(def tyhja-tr-osoite {:numero nil :alkuosa nil :alkuetaisyys nil :loppuosa nil :loppuetaisyys nil})

(defn toteuman-parametrit [toteuma kayttaja]
  (merge tyhja-tr-osoite
         (:tr toteuma)
         {:urakka      (:urakka-id toteuma)
          :sopimus     (:sopimus-id toteuma)
          :alkanut     (konv/sql-timestamp (:alkanut toteuma))
          :paattynyt   (konv/sql-timestamp (or (:paattynyt toteuma)
                                               (:alkanut toteuma)))
          :tyyppi      (name (:tyyppi toteuma))
          :kayttaja    (:id kayttaja)
          :suorittaja  (:suorittajan-nimi toteuma)
          :ytunnus     (:suorittajan-ytunnus toteuma)
          :lisatieto   (:lisatieto toteuma)
          :ulkoinen_id nil
          :lahde       "harja-ui"}))

(defn toteumatehtavan-parametrit [toteuma kayttaja]
  [(get-in toteuma [:tehtava :toimenpidekoodi]) (get-in toteuma [:tehtava :maara]) (:id kayttaja)
   (get-in toteuma [:tehtava :paivanhinta]) (:urakka-id toteuma)])

(defn toteumatyypin-oikeustarkistus
  "Tarkistaa toteumatyypin mukaisen lukuoikeuden.
   Toteuman kuuluminen urakkaan täytyy tarkistaa erikseen."
  [db user urakka-id toteuma-id]
  (let [toteuman-tyyppi (:tyyppi (first (toteumat-q/toteuman-tyyppi db toteuma-id)))
        _ (case toteuman-tyyppi
            "yksikkohintainen"
            (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-yksikkohintaisettyot   user urakka-id)
            "kokonaishintainen"
            (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot   user urakka-id)
            ;; Kaikki seuraavat ovat pohjimmiltaan muutos- ja lisätöitä
            "muutostyo"
            (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-muutos-ja-lisatyot user urakka-id)
            "lisatyo"
            (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-muutos-ja-lisatyot user urakka-id)
            "akillinen-hoitotyo"
            (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-muutos-ja-lisatyot user urakka-id)
            "vahinkojen-korjaukset"
            (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-muutos-ja-lisatyot user urakka-id))]))

(defn hae-urakan-toteuma [db user {:keys [urakka-id toteuma-id]}]
  (log/debug "Haetaan urakan toteuma id:llä: " toteuma-id)
  (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db toteuma-id urakka-id)
  (toteumatyypin-oikeustarkistus db user urakka-id toteuma-id)
  (let [toteuma (konv/sarakkeet-vektoriin
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
          {:urakka     urakka-id
           :sopimus    sopimus-id
           :alkanut    (konv/sql-date alkupvm)
           :paattynyt  (konv/sql-date loppupvm)
           :tyyppi     (name tyyppi)
           :toimenpide toimenpide-id
           :tehtava    tehtava-id})))

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

(defn- kasittele-toteumatehtava [c user toteuma tehtava]
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
        ;; Koska tälle funktiolle lähetetään kahdesta paikasta eri mäpin avaimessa toteuman id.
        ;; Hyväksytään toteuman id jommasta kummasta avaimesta.
        (toteumat-q/luo-tehtava<! c
          (or (get-in toteuma [:toteuma :id])
            (:toteuma-id toteuma))
          (:toimenpidekoodi tehtava) (:maara tehtava) (:id user) nil (:urakka-id toteuma)))))
  (let [toteumatyyppi (name (:tyyppi toteuma))
        maksueratyyppi (case toteumatyyppi
                         "muutostyo" "muu"
                         "vahinkojen-korjaukset" "muu"
                         toteumatyyppi)]
    (toteumat-q/merkitse-toteuman-maksuera-likaiseksi! c
                                                       maksueratyyppi
                                                       (:toimenpidekoodi tehtava)
                                                       (:urakka-id toteuma))))

(defn- kasittele-toteuman-tehtavat [c user toteuma]
  (doseq [tehtava (:tehtavat toteuma)]
    (kasittele-toteumatehtava c user toteuma tehtava)))

(defn paivita-toteuman-reitti [db id reitti]
  (when reitti
    (toteumat-q/paivita-toteuman-reitti! db {:id     id
                                             :reitti (geometriaksi reitti)})))

(defn- paivita-toteuma [c user toteuma]
  (toteumat-q/paivita-toteuma<! c (assoc (toteuman-parametrit toteuma user)
                                    :id (:toteuma-id toteuma)))
  (paivita-toteuman-reitti c (:toteuma-id toteuma) (:reitti toteuma))
  (kasittele-toteuman-tehtavat c user toteuma)
  (:toteuma-id toteuma))

(defn- luo-toteuma [c user toteuma]
  (let [toteuman-parametrit (-> (toteuman-parametrit toteuma user) (assoc :reitti (geometriaksi (:reitti toteuma))
                                                                          :tyokonetyyppi nil :tyokonetunniste nil
                                                                          :tyokoneen-lisatieto nil))
        id (toteumat-q/luo-uusi-toteuma c toteuman-parametrit)
        toteumatyyppi (name (:tyyppi toteuma))]
    (if (and (= "kokonaishintainen" (:tyyppi toteuman-parametrit))
             (nil? (:reitti toteuman-parametrit)))
      {:virhe "Kokonaishintainen toteuma vaatii reitin. Reitti oli tyhjä."}

      (do
        (doseq [{:keys [toimenpidekoodi maara]} (:tehtavat toteuma)]
          (toteumat-q/luo-tehtava<! c id toimenpidekoodi maara (:id user) nil (:urakka toteuman-parametrit))
          (toteumat-q/merkitse-toteuman-maksuera-likaiseksi! c
                                                             toteumatyyppi
                                                             toimenpidekoodi
                                                             (:urakka toteuman-parametrit)))
        id))))

(defn- hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
  [db user {:keys [urakka-id sopimus-id alkupvm loppupvm toimenpide tehtava]}]
  (log/debug "Aikaväli: " (pr-str alkupvm) (pr-str loppupvm))
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (let [toteumat (mapv #(konv/alaviiva->rakenne %)
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
  (jdbc/with-db-transaction
    [c db]
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
                                  (toteumat-q/paivita-toteuman-tehtava! c (:toimenpidekoodi tehtava) (or (:maara tehtava) 0) (boolean (:poistettu tehtava))
                                                                        (:paivanhinta tehtava) (:tehtava_id tehtava)))

                                (log/debug "Merkitään tehtavien: " tehtavatidt " maksuerät likaisiksi")
                                (toteumat-q/merkitse-toteumatehtavien-maksuerat-likaisiksi! c tehtavatidt))))

  (let [paivitetyt-tehtavat (hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db user
                                                                                {:urakka-id       urakka-id
                                                                                 :sopimus-id      sopimus-id
                                                                                 :alkupvm         alkupvm
                                                                                 :loppupvm        loppupvm
                                                                                 :tyyppi          tyyppi
                                                                                 :toimenpidekoodi (:toimenpidekoodi (first tehtavat))})
        paivitetyt-summat (hae-urakan-toteumien-tehtavien-summat db user
                                                                 {:urakka-id     urakka-id
                                                                  :sopimus-id    sopimus-id
                                                                  :alkupvm       alkupvm
                                                                  :loppupvm      loppupvm
                                                                  :toimenpide-id toimenpide-id
                                                                  :tyyppi        tyyppi})]
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
  (if (or (oikeudet/voi-lukea? oikeudet/urakat-toteumat-erilliskustannukset urakka-id user)
          (oikeudet/voi-lukea? oikeudet/urakat-toteumat-vesivaylaerilliskustannukset urakka-id user))
    (do
      (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
      (into []
          erilliskustannus-xf
          (toteumat-q/listaa-urakan-hoitokauden-erilliskustannukset db urakka-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))

    (throw+ (roolit/->EiOikeutta "Ei oikeutta"))))

(defn tallenna-erilliskustannus [db user {:keys [palauta-tallennettu?] :as ek}]
  (log/debug "tallenna erilliskustannus:" ek)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-erilliskustannukset user (:urakka-id ek))
  (tarkistukset/vaadi-erilliskustannus-kuuluu-urakkaan db (:id ek) (:urakka-id ek))
  (if (or (oikeudet/voi-lukea? oikeudet/urakat-toteumat-erilliskustannukset (:urakka-id ek) user)
          (oikeudet/voi-lukea? oikeudet/urakat-toteumat-vesivaylaerilliskustannukset (:urakka-id ek) user))
    (jdbc/with-db-transaction
      [db db]      
      (let [{:keys [tyyppi urakka-id sopimus toimenpideinstanssi
                    pvm rahasumma indeksin_nimi lisatieto poistettu id
                    kasittelytapa laskutuskuukausi liitteet]} ek
            ;; varmista sopimus (id) mikäli sitä ei ole annettu
            sopimus (if (nil? sopimus)
                      (:id (first (sopimukset-q/hae-urakan-paasopimus db urakka-id)))
                      sopimus)
            parametrit {:tyyppi tyyppi
                        :urakka urakka-id
                        :sopimus sopimus
                        :toimenpideinstanssi toimenpideinstanssi
                        :pvm (konv/sql-date pvm)
                        :rahasumma rahasumma
                        :indeksin_nimi indeksin_nimi
                        :lisatieto lisatieto
                        :laskutuskuukausi laskutuskuukausi
                        :kasittelytapa (when kasittelytapa (name kasittelytapa))
                        :luoja (:id user)}
            tallennettu (if (not id)
                          (toteumat-q/luo-erilliskustannus<! db parametrit)
                          (toteumat-q/paivita-erilliskustannus! db (merge (dissoc parametrit :luoja)
                                                                     {:poistettu (or poistettu false)
                                                                      :id id
                                                                      :muokkaaja (:id user)})))
            ;; Päivitys tai tallennus ei laske bonukselle indeksikorotusta, joten haetaan erilliskustannus uusiksi mahdollisen indeksikorotuksen kanssa
            tallennettu (first (erilliskustannus-kyselyt/hae-erilliskustannus db {:urakka-id urakka-id
                                                                                  :id (:id tallennettu)}))
            bonuksen-liitteet-tietokannasta (when id
                                              (laatupoikkeamat-kyselyt/hae-bonuksen-liitteet db id))]
        (when (not (empty? liitteet))
          (doseq [l liitteet]
            (let [onko-liite-kannassa? (some #(when (and %
                                                      (= (:id %) (:id l)))
                                                true) bonuksen-liitteet-tietokannasta)]
              (when-not onko-liite-kannassa?
                (toteumat-q/tallenna-erilliskustannukselle-liitteet<! db {:bonus (or (:id tallennettu) id) :liite (:id l)})))))
        (toteumat-q/merkitse-toimenpideinstanssin-maksuera-likaiseksi! db (:toimenpideinstanssi ek))
        (cond
          (and
            palauta-tallennettu?
            (map? tallennettu))
          tallennettu

          (and palauta-tallennettu?
            (number? tallennettu))
          ek

          :else
          (hae-urakan-erilliskustannukset db user {:urakka-id (:urakka-id ek)
                                                   :alkupvm   (:alkupvm ek)
                                                   :loppupvm  (:loppupvm ek)}))))

    (throw+ (roolit/->EiOikeutta "Ei oikeutta"))))

(defn poista-erilliskustannus
  "Merkitään erilliskustannus poistetuksi"
  [db user {erilliskustannus-id :id urakka-id :urakka-id :as ek}]
  (assert (integer? erilliskustannus-id) "Parametria 'erilliskustannus-id' ei ole määritelty")
  (assert (integer? urakka-id) "Parametria 'urakka-id' ei ole määritelty")

  (log/debug "Poista erilliskustannus:" ek)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-erilliskustannukset user urakka-id)
  (tarkistukset/vaadi-erilliskustannus-kuuluu-urakkaan db erilliskustannus-id urakka-id)

  (if (or (oikeudet/voi-lukea? oikeudet/urakat-toteumat-erilliskustannukset urakka-id user)
        (oikeudet/voi-lukea? oikeudet/urakat-toteumat-vesivaylaerilliskustannukset urakka-id user))

    (jdbc/with-db-transaction
      [db db]
      (let [poistettu (toteumat-q/poista-erilliskustannus<! db {:id erilliskustannus-id
                                                               :muokkaaja (:id user)
                                                               :urakka urakka-id})]
        (when (:toimenpideinstanssi poistettu)
          (toteumat-q/merkitse-toimenpideinstanssin-maksuera-likaiseksi! db (:toimenpideinstanssi poistettu)))
        erilliskustannus-id))

    (throw+ (roolit/->EiOikeutta "Ei oikeutta"))))

(defn hae-tehtavan-toteumat [db user {:keys [urakka-id toimenpidekoodi-id hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (let [alkupvm (str hoitokauden-alkuvuosi "-10-01")
        loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")
        res (toteumat-q/listaa-tehtavan-toteumat db {:urakka urakka-id
                                                     :toimenpidekoodi-id toimenpidekoodi-id
                                                     :alkupvm alkupvm
                                                     :loppupvm loppupvm
                                                     :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})]
    res))

(defn hae-toimenpiteen-maarien-toteumat [db user {:keys [urakka-id tehtavaryhma hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (let [t (if (or (= "Kaikki" tehtavaryhma) (= 0 tehtavaryhma)) nil tehtavaryhma)
        alkupvm (str hoitokauden-alkuvuosi "-10-01")
        loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")
        vastaus (toteumat-q/listaa-urakan-maarien-toteumat db {:urakka urakka-id
                                                               :tehtavaryhma t
                                                               :alkupvm alkupvm
                                                               :loppupvm loppupvm
                                                               :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})]
    vastaus))

(defn hae-urakan-toimenpiteet [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (toteumat-q/listaa-urakan-toteutumien-toimenpiteet db))

(defn hae-maarien-toteumien-toimenpiteiden-tehtavat [db user {:keys [urakka-id tehtavaryhma otsikko]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (toteumat-q/listaa-maarien-toteumien-toimenpiteiden-tehtavat db {:urakka urakka-id :otsikko otsikko}))

(defn poista-maarien-toteuma! [db user {:keys [urakka-id toteuma-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (do
    (log/debug "poista-maarien-toteuma! :: toteuma-id urakka-id" toteuma-id urakka-id)
    (jdbc/with-db-transaction [tx db]
      (toteumat-q/poista-toteuma! tx (:id user) toteuma-id)
      (toteumat-q/poista-toteuma-tehtava! tx {:kayttaja (:id user)
                                              :toteuma-id toteuma-id}))))

;(def ___malli
;    {
;     ; yleiset tiedot
;     :toimenpide 1
;     :urakka-id  1
;     :tyyppi     :keyword
;     :pvm        1
;     :toteumat   [{
;                   :toteuma-id         1 - muokatessa
;                   :toteuma-tehtava-id 1 - muokatessa
;                   :maara              1
;                   :lisatieto          "asd"
;                   :tehtava            1
;                   :sijainti           {}
;                   ; nämä yllä ovat määrämitattavalla
;                   :tehtava            1
;                   :lisatieto          "adf"
;                   :sijainti           {}
;                   ; mämä yllä ovat äkillisellä
;                   :lisatieto          "asf"
;                   :sijainti           {}
;                   ; mämä ovat lisätyöllä
;                   }]
;     })

(defn tallenna-toteuma! [db user {:keys [tyyppi urakka-id loppupvm toteumat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (let [loppupvm (konv/sql-date loppupvm)
        sopimus (first (fetch db ::sopimus/sopimus #{::sopimus/id} {::sopimus/urakka-id urakka-id}))
        ;; Tyyppivaihtoehtoja on kolme. "kokonaishintainen" on määrien toteumille, "lisatyo" on lisätöille
        ;; ja "akillinen-hoitotyo" Äkillisille hoitotöille
        tyyppi (case tyyppi
                 :maaramitattava "kokonaishintainen"
                 :akillinen-hoitotyo "akillinen-hoitotyo"
                 :tilaajan-varaukset "muut-rahavaraukset"
                 :vahinkojen-korjaukset "vahinkojen-korjaukset"
                 :lisatyo "lisatyo"
                 "kokonaishintainen")]
    ; vain määrämitattavilla on määrä... niinhän se nimikin sanoo, tosiaan :D
    (if (some #(and
                 (= "kokonaishintainen" tyyppi)
                 (> 0 (:maara %))) toteumat)
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Tarkista määrä."}]})
      (jdbc/with-db-transaction [db db]
        (doall
          (for [{:keys [maara lisatieto tehtava toteuma-id toteuma-tehtava-id poistettu]
                 {:keys [numero alkuosa alkuetaisyys loppuosa loppuetaisyys]} :sijainti
                 :as _toteuma} toteumat]
            (if poistettu
              (poista-maarien-toteuma! db user {:urakka-id urakka-id
                                                :toteuma-id toteuma-id})
              (let [toteuma-id (if toteuma-id
                                 (do
                                   (update! db ::toteuma/toteuma
                                     {::toteuma/id toteuma-id
                                      ::muokkaustiedot/muokkaaja-id (:id user)
                                      ::muokkaustiedot/muokattu (pvm/nyt)
                                      ::toteuma/urakka-id urakka-id
                                      ::toteuma/alkanut loppupvm
                                      ::toteuma/paattynyt loppupvm
                                      ::toteuma/tyyppi tyyppi
                                      ::toteuma/lahde "harja-ui"
                                      ::toteuma/tr-numero numero
                                      ::toteuma/tr-alkuosa alkuosa
                                      ::toteuma/tr-alkuetaisyys alkuetaisyys
                                      ::toteuma/tr-loppuetaisyys loppuetaisyys
                                      ::toteuma/tr-loppuosa loppuosa
                                      ::toteuma/sopimus-id (::sopimus/id sopimus)}
                                     {::toteuma/id toteuma-id})
                                   toteuma-id)
                                 (toteumat-q/luo-uusi-toteuma db
                                   {:urakka urakka-id
                                    :sopimus (::sopimus/id sopimus)
                                    :alkanut loppupvm
                                    :paattynyt loppupvm
                                    :tyyppi tyyppi
                                    :luotu (pvm/nyt)
                                    :kayttaja (:id user)
                                    :suorittaja nil
                                    :ytunnus nil
                                    :lisatieto nil
                                    :ulkoinen_id nil
                                    :reitti nil
                                    :numero numero
                                    :alkuosa alkuosa
                                    :alkuetaisyys alkuetaisyys
                                    :loppuosa loppuosa
                                    :loppuetaisyys loppuetaisyys
                                    :lahde "harja-ui",
                                    :tyokonetyyppi nil
                                    :tyokonetunniste nil
                                    :tyokoneen-lisatieto nil}))
                    tt (upsert! db ::toteuma/toteuma-tehtava
                         (merge (if toteuma-tehtava-id
                                  {::toteuma/id toteuma-tehtava-id
                                   ::toteuma/muokattu (pvm/nyt)
                                   ::toteuma/muokkaaja (:id user)}
                                  {::toteuma/luotu (pvm/nyt)
                                   ::toteuma/luoja (:id user)})
                           {::toteuma/toteuma-id toteuma-id
                            ::toteuma/urakka_id urakka-id
                            ::toteuma/muokattu (pvm/nyt)
                            ::toteuma/toimenpidekoodi (:id tehtava)
                            ::toteuma/maara (case tyyppi
                                              "kokonaishintainen"
                                              (when maara (bigdec maara))

                                              ("akillinen-hoitotyo" "lisatyo" "muut-rahavaraukset" "vahinkojen-korjaukset")
                                              (bigdec 1))
                            ::toteuma/tehtava-lisatieto lisatieto}))]

                toteuma-id))))))))

(defn hae-maarien-toteuma [db user {:keys [id urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
  (let [toteuma (first (toteumat-q/hae-maarien-toteuma db {:id id}))
        reitti-viiva (tr-q/hae-tr-viiva db
                       {:numero (:sijainti_numero toteuma)
                        :alkuosa (:sijainti_alku toteuma)
                        :alkuetaisyys (:sijainti_alkuetaisyys toteuma)
                        :loppuosa (:sijainti_loppu toteuma)
                        :loppuetaisyys (:sijainti_loppuetaisyys toteuma)})
        reitti (when (and
                       (not (nil? reitti-viiva))
                       (nil? (:virhe reitti-viiva)))
                 (geo/geometry (geo/clj->pg reitti-viiva)))
        toteuma (if (:sijainti_loppuetaisyys toteuma)
                  (assoc toteuma :reitti reitti)
                  toteuma)
        _ (log/debug "Haettu toteuma id:lle: " id " toteuma: " (pr-str toteuma))]
    toteuma))

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
  (-> (into []
            muut-tyot-xf
            (toteumat-q/listaa-urakan-hoitokauden-toteumat-muut-tyot db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm)))
      (konv/sarakkeet-vektoriin
        {:liite :liitteet}
        :id)))

(defn- paivita-muun-tyon-toteuma
  [c user toteuma]
  (log/debug "Päivitä toteuma" toteuma)
  (if (:poistettu toteuma)
    (let [params [c (:id user) (get-in toteuma [:toteuma :id])]
          toteumatyyppi (name (:tyyppi toteuma))
          maksueratyyppi (case toteumatyyppi
                           "akillinen-hoitotyo" "akillinen-hoitotyo"
                           "lisatyo" "lisatyo"
                           "muu")]
      (log/debug "poista toteuma" (get-in toteuma [:toteuma :id]))
      (apply toteumat-q/poista-toteuman-tehtavat! params)
      (apply toteumat-q/poista-toteuma! params)
      (toteumat-q/merkitse-toteuman-maksuera-likaiseksi! c
                                                         maksueratyyppi
                                                         (:toimenpidekoodi (:tehtava toteuma))
                                                         (:urakka-id toteuma)))
    (let [paivitetty (toteumat-q/paivita-toteuma<! c {:alkanut       (konv/sql-date (:alkanut toteuma))
                                                      :paattynyt     (konv/sql-date (:paattynyt toteuma))
                                                      :tyyppi        (name (:tyyppi toteuma))
                                                      :kayttaja      (:id user)
                                                      :suorittaja    (:suorittajan-nimi toteuma)
                                                      :ytunnus       (:suorittajan-ytunnus toteuma)
                                                      :lisatieto     (:lisatieto toteuma)
                                                      :numero        (get-in toteuma [:tr :numero])
                                                      :alkuosa       (get-in toteuma [:tr :alkuosa])
                                                      :alkuetaisyys  (get-in toteuma [:tr :alkuetaisyys])
                                                      :loppuosa      (get-in toteuma [:tr :loppuosa])
                                                      :loppuetaisyys (get-in toteuma [:tr :loppuetaisyys])
                                                      :id            (get-in toteuma [:toteuma :id])
                                                      :urakka        (:urakka-id toteuma)})
          id (:id paivitetty)]
      (paivita-toteuman-reitti c (get-in toteuma [:toteuma :id]) (:reitti toteuma))
      (kasittele-toteumatehtava c user toteuma (assoc (:tehtava toteuma)
                                                 :tehtava-id (get-in toteuma [:tehtava :id])))
      id)))

(defn- luo-muun-tyon-toteuma
  [c user toteuma]
  (log/debug "Luodaan uusi toteuma" toteuma)
  (let [toteuman-parametrit (-> (toteuman-parametrit toteuma user) (assoc :reitti (geometriaksi (:reitti toteuma))
                                                                          :tyokonetyyppi nil :tyokonetunniste nil
                                                                          :tyokoneen-lisatieto nil))
        id (toteumat-q/luo-uusi-toteuma c toteuman-parametrit)
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
    (toteumat-q/merkitse-toteuman-maksuera-likaiseksi! c maksueratyyppi
                                                       toimenpidekoodi
                                                       (:urakka toteuman-parametrit))
    id))

(defn tallenna-muiden-toiden-toteuma
  [db user toteuma]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-muutos-ja-lisatyot user (:urakka-id toteuma))
  (jdbc/with-db-transaction [db db]
                            (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db (get-in toteuma [:toteuma :id]) (:urakka-id toteuma))
                            (tarkistukset/vaadi-toteuma-ei-jarjestelman-luoma db (get-in toteuma [:toteuma :id]))
                            (let [id (if (get-in toteuma [:toteuma :id])
                                       (paivita-muun-tyon-toteuma db user toteuma)
                                       (luo-muun-tyon-toteuma db user toteuma))]
                              ;; tallenna liitteet
                              (doseq [liite (:uudet-liitteet toteuma)]
                                (toteumat-q/tallenna-liite-toteumalle<! db id (:id liite)))

                              ;; lisätään tarvittaessa hinta muutoshintainen_tyo tauluun
                              (when (:uusi-muutoshintainen-tyo toteuma)
                                (let [parametrit [db (:yksikko toteuma) (:yksikkohinta toteuma) (:id user)
                                                  (:urakka-id toteuma) (:sopimus-id toteuma) (get-in toteuma [:tehtava :toimenpidekoodi])
                                                  (konv/sql-date (:urakan-alkupvm toteuma))
                                                  (konv/sql-date (:urakan-loppupvm toteuma))]]
                                  (apply mht-q/lisaa-muutoshintainen-tyo<! parametrit)))
                              (hae-urakan-muut-tyot db user
                                                    {:urakka-id  (:urakka-id toteuma)
                                                     :sopimus-id (:sopimus-id toteuma)
                                                     :alkupvm    (konv/sql-timestamp (:hoitokausi-aloituspvm toteuma))
                                                     :loppupvm   (konv/sql-timestamp (:hoitokausi-lopetuspvm toteuma))}))))

(defn tallenna-toteuma-ja-toteumamateriaalit
  "Tallentaa toteuman ja toteuma-materiaalin, ja palauttaa lopuksi kaikki urakassa käytetyt materiaalit
  (yksi rivi per materiaali).
  Tiedon mukana tulee yhteenlaskettu summa materiaalin käytöstä.
  * Jos tähän funktioon tehdään muutoksia, pitäisi muutokset tehdä myös
  materiaalit/tallenna-toteuma-materiaaleja! funktioon (todnäk)"
  [db user t toteumamateriaalit hoitokausi sopimus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-materiaalit user (:urakka t))
  (log/debug "Tallenna toteuma: " (pr-str t) " ja toteumamateriaalit " (pr-str toteumamateriaalit))
  (jdbc/with-db-transaction [c db]
                            (tarkistukset/vaadi-toteuma-kuuluu-urakkaan c (:id t) (:urakka t))
                            (tarkistukset/vaadi-toteuma-ei-jarjestelman-luoma c (:id t))
                            ;; Jos toteumalla on positiivinen id, toteuma on olemassa
                            (let [urakka-id (:urakka t)
                                  toteuma-id (:id t)
                                  tr-osoite (:tierekisteriosoite t)
                                  reitti-viiva (when tr-osoite (tr-q/hae-tr-viiva c tr-osoite))
                                  _ (log/debug "REITTI:" (pr-str reitti-viiva))
                                  toteuman-alkuperainen-pvm (toteumat-q/hae-toteuman-alkanut-pvm-idlla c {:id toteuma-id})
                                  toteuma (if (id/id-olemassa? toteuma-id)
                                            ;; Jos poistettu=true, halutaan toteuma poistaa.
                                            ;; Molemmissa tapauksissa parametrina saatu toteuma tulee palauttaa
                                            (if (:poistettu t)
                                              (do
                                                (log/debug "Poistetaan toteuma " (:id t))
                                                (toteumat-q/poista-toteuma! c (:id user) (:id t))
                                                t)
                                              (do
                                                (log/debug "Pävitetään toteumaa " (:id t))
                                                (toteumat-q/paivita-toteuma<! c {:alkanut       (konv/sql-date (:alkanut t))
                                                                                 :paattynyt     (konv/sql-date (:paattynyt t))
                                                                                 :tyyppi        (:tyyppi t)
                                                                                 :kayttaja      (:id user)
                                                                                 :suorittaja    (:suorittajan-nimi t)
                                                                                 :ytunnus       (:suorittajan-ytunnus t)
                                                                                 :lisatieto     (:lisatieto t)
                                                                                 :numero        (:numero tr-osoite)
                                                                                 :alkuosa       (:alkuosa tr-osoite)
                                                                                 :alkuetaisyys  (:alkuetaisyys tr-osoite)
                                                                                 :loppuosa      (:loppuosa tr-osoite)
                                                                                 :loppuetaisyys (:loppuetaisyys tr-osoite)
                                                                                 :id            (:id t)
                                                                                 :urakka        urakka-id})
                                                (paivita-toteuman-reitti c (:id t) reitti-viiva)
                                                t))
                                            ;; Jos id:tä ei ole tai se on negatiivinen, halutaan luoda uusi toteuma
                                            ;; Tässä tapauksessa palautetaan kyselyn luoma toteuma
                                            (do
                                              (log/debug "Luodaan uusi toteuma")
                                              (let [toteuman-id (toteumat-q/luo-uusi-toteuma c
                                                                                             {:urakka              urakka-id
                                                                                              :sopimus             (:sopimus t)
                                                                                              :alkanut             (konv/sql-date (:alkanut t))
                                                                                              :paattynyt           (konv/sql-date (:paattynyt t))
                                                                                              :tyyppi              (:tyyppi t)
                                                                                              :kayttaja            (:id user)
                                                                                              :suorittaja          (:suorittajan-nimi t)
                                                                                              :ytunnus             (:suorittajan-ytunnus t)
                                                                                              :lisatieto           (:lisatieto t)
                                                                                              :ulkoinen_id         nil
                                                                                              :reitti              (geometriaksi reitti-viiva)
                                                                                              :numero              (:numero tr-osoite)
                                                                                              :alkuosa             (:alkuosa tr-osoite)
                                                                                              :alkuetaisyys        (:alkuetaisyys tr-osoite)
                                                                                              :loppuosa            (:loppuosa tr-osoite)
                                                                                              :loppuetaisyys       (:loppuetaisyys tr-osoite)
                                                                                              :lahde               "harja-ui"
                                                                                              :tyokonetyyppi       nil
                                                                                              :tyokonetunniste     nil
                                                                                              :tyokoneen-lisatieto nil})
                                                    tot {:id toteuman-id :urakka urakka-id}]
                                                tot)))
                                  urakan-sopimus-idt (map :id (sopimukset-q/hae-urakan-sopimus-idt c {:urakka_id urakka-id}))]
                              (log/debug "Toteuman tallentamisen tulos:" (pr-str toteuma))

                              (doseq [tm toteumamateriaalit]
                                ;; Positiivinen id = luodaan tai poistetaan toteuma-materiaali
                                (if (id/id-olemassa? (:id tm))
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
                                                                            (:maara tm) (:id user) (:urakka toteuma)))))

                              ;; Hanskataan tässä epämieluisa kulmatapaus: toteuman pvm saattaa muuttua, ja tietokantacachet
                              ;; pitää laittaa jiiriin sekä vanhan että uuden pvm:n osalta joka toteumalle
                              (when-not (= (:alkanut t) toteuman-alkuperainen-pvm)
                                (doseq [sopimus-id urakan-sopimus-idt]
                                  (materiaalit-q/paivita-sopimuksen-materiaalin-kaytto c {:sopimus sopimus-id
                                                                                          :alkupvm toteuman-alkuperainen-pvm}))
                                (materiaalit-q/paivita-urakan-materiaalin-kaytto-hoitoluokittain c {:urakka urakka-id
                                                                                                    :alkupvm toteuman-alkuperainen-pvm
                                                                                                    :loppupvm toteuman-alkuperainen-pvm}))

                              ;; Tässä cachejen päivitys uuden pvm:n osalta
                              (doseq [sopimus-id urakan-sopimus-idt]
                                (materiaalit-q/paivita-sopimuksen-materiaalin-kaytto c {:sopimus sopimus-id
                                                                                        :alkupvm (:alkanut t)}))
                              (materiaalit-q/paivita-urakan-materiaalin-kaytto-hoitoluokittain c {:urakka urakka-id
                                                                                                  :alkupvm (:alkanut t)
                                                                                                  :loppupvm (:alkanut t)})

                              ;; Jos saatiin parametrina hoitokausi, voidaan palauttaa urakassa käytetyt materiaalit
                              ;; Tämä ei ole ehkä paras mahdollinen tapa hoitaa tätä, mutta toteuma/materiaalit näkymässä
                              ;; tarvitaan tätä tietoa. -Teemu K
                              (when hoitokausi
                                (materiaalipalvelut/hae-urakassa-kaytetyt-materiaalit c user (:urakka toteuma)
                                                                                      (first hoitokausi) (second hoitokausi)
                                                                                      sopimus)))))

(defn varustetoteuma-xf
  "Palauttaa transducerin tietokannasta haettavien varustetoteumien muuntamiseen."
  []
  (comp
    (map #(assoc % :tyyppi-kartalla :varustetoteuma))
    (map #(konv/string->keyword % :toimenpide))
    (map #(konv/string->keyword % :toteumatyyppi))
    (harja.geo/muunna-pg-tulokset :reittipiste_sijainti)

    (map konv/alaviiva->rakenne)))

(defn hae-urakan-varustetoteumat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tienumero tietolajit] :as hakuehdot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (log/debug "Haetaan varustetoteumat: " urakka-id sopimus-id alkupvm loppupvm tienumero)
  (let [toteumat (into []
                       (toteumat-q/hae-urakan-varustetoteumat
                         db
                         {:urakka             urakka-id
                          :sopimus            sopimus-id
                          :alkupvm            (konv/sql-date alkupvm)
                          :loppupvm           (konv/sql-date loppupvm)
                          :rajaa_tienumerolla (boolean tienumero)
                          :tienumero          tienumero
                          :tietolajit         (when tietolajit
                                                (konv/seq->array tietolajit))}))
        toteumat (konv/sarakkeet-vektoriin
                   toteumat
                   {:reittipiste    :reittipisteet
                    :toteumatehtava :toteumatehtavat}
                   :id)]
    (log/debug "Palautetaan " (count toteumat) " varustetoteuma(a)")
    toteumat))

(defn hae-kokonaishintaisen-toteuman-tiedot
  ([db user urakka-id pvm toimenpidekoodi]
   (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
   (into []
         (map konv/alaviiva->rakenne)
         (toteumat-q/hae-kokonaishintaisen-toteuman-tiedot
           db {:urakka          urakka-id
               :pvm             pvm
               :toimenpidekoodi toimenpidekoodi
               :toteuma         nil})))
  ([db user urakka-id toteuma-id]
   (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user urakka-id)
   (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db toteuma-id urakka-id)
   (into []
         (map konv/alaviiva->rakenne)
         (toteumat-q/hae-kokonaishintaisen-toteuman-tiedot
           db {:urakka          urakka-id
               :toteuma         toteuma-id
               :pvm             nil
               :toimenpidekoodi nil}))))

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
    {:tehtava           :tehtavat
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
            (merge {:x               x :y y :tyyppi "yksikkohintainen"
                    :toimenpidekoodi nil}
                   parametrit)))
    {:tehtava           :tehtavat
     :materiaalitoteuma :materiaalit}))

(defn- siirry-toteuma
  "Palauttaa frontin tarvitsemat tiedot, joilla toteumaan voidaan siirtyä"
  [db user toteuma-id]
  (let [urakka-id (:urakka (first
                             (toteumat-q/toteuman-urakka
                               db {:toteuma toteuma-id})))]
    (toteumatyypin-oikeustarkistus db user urakka-id toteuma-id)
    (first
      (konv/sarakkeet-vektoriin
        (into []
              (map konv/alaviiva->rakenne)
              (toteumat-q/siirry-toteuma
                db {:toteuma-id       toteuma-id
                    :tarkista-urakka? (= :urakoitsija (roolit/osapuoli user))
                    :urakoitsija-id   (get-in user [:organisaatio :id])}))
        {:tehtava :tehtavat} :id (constantly true)))))

(defn hae-toteumien-reitit [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-kokonaishintaisettyot user (:urakka-id tiedot))
  (let [idt (into [] (:idt tiedot))]
    (toteumat-q/hae-toteumien-reitit db {:idt idt :urakka-id (:urakka-id tiedot)})))

(defn- hae-toteuman-liitteet [db user {:keys [urakka-id toteuma-id oikeus]}]
  (let [nst-joista-saa-kutsua-toteuman-liitteden-hakua #{'urakat-toteumat-varusteet}] ;; voit lisätä oikeuksia tarpeen mukaan
    (when (nst-joista-saa-kutsua-toteuman-liitteden-hakua oikeus)
      (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db toteuma-id urakka-id)
      (oikeudet/vaadi-lukuoikeus (deref (ns-resolve 'harja.domain.oikeudet oikeus)) user urakka-id)
      (toteumat-q/hae-toteuman-liitteet db toteuma-id))))

(defrecord Toteumat []
  component/Lifecycle
  (start [{http         :http-palvelin
           db           :db
           db-replica   :db-replica
           karttakuvat  :karttakuvat
           :as          this}]
    (assert (some? db-replica))
    (when karttakuvat
      (karttakuvat/rekisteroi-karttakuvan-lahde!
        karttakuvat :kokonaishintainen-toteuma
        (partial #'hae-kokonaishintainen-toteuma-kartalle db-replica)
        (partial #'hae-kokonaishintaisen-toteuman-tiedot-kartalle db-replica)
        "kht")
      (karttakuvat/rekisteroi-karttakuvan-lahde!
        karttakuvat :yksikkohintaiset-toteumat
        (partial #'hae-yksikkohintaiset-toteumat-kartalle db-replica)
        (partial #'hae-yksikkohintaisen-toteuman-tiedot-kartalle db-replica)
        "yht"))

    (julkaise-palvelut
      http
      :urakan-toteuma
      (fn [user tiedot]
        (hae-urakan-toteuma db-replica user tiedot))
      :urakan-toteumien-tehtavien-summat
      (fn [user tiedot]
        (hae-urakan-toteumien-tehtavien-summat db-replica user tiedot))
      :urakan-toteutuneet-tehtavat-toimenpidekoodilla
      (fn [user tiedot]
        (hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db-replica user tiedot))
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
        (hae-urakan-erilliskustannukset db-replica user tiedot))
      :tallenna-erilliskustannus
      (fn [user toteuma]
        (tallenna-erilliskustannus db user toteuma))
      :poista-erilliskustannus
      (fn [user erilliskustannus]
        (poista-erilliskustannus db user erilliskustannus))
      :urakan-toteumien-toimenpiteet
      (fn [user tiedot]
        (hae-urakan-toimenpiteet db-replica user tiedot))
      :maarien-toteutumien-toimenpiteiden-tehtavat
      (fn [user tiedot]
        (hae-maarien-toteumien-toimenpiteiden-tehtavat db-replica user tiedot))
      :tallenna-toteuma
      (fn [user tiedot]
        (tallenna-toteuma! db user tiedot))
      :hae-maarien-toteuma
      (fn [user tiedot]
        (hae-maarien-toteuma db-replica user tiedot))
      :poista-toteuma
      (fn [user tiedot]
        (poista-maarien-toteuma! db user tiedot))
      :hae-toimenpiteen-tehtava-yhteenveto
      (fn [user tiedot]
        (hae-toimenpiteen-maarien-toteumat db-replica user tiedot))
      :hae-tehtavan-toteumat
      (fn [user tiedot]
        (hae-tehtavan-toteumat db-replica user tiedot))
      :urakan-toteutuneet-muut-tyot
      (fn [user tiedot]
        (hae-urakan-muut-tyot db-replica user tiedot))
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
          (hae-kokonaishintaisen-toteuman-tiedot db-replica user urakka-id toteuma-id)
          (hae-kokonaishintaisen-toteuman-tiedot db-replica user urakka-id pvm toimenpidekoodi)))
      :urakan-varustetoteumat
      (fn [user tiedot]
        (hae-urakan-varustetoteumat db-replica user tiedot))
      :hae-toteuman-reitti-ja-tr-osoite
      (fn [user tiedot]
        (hae-toteuman-reitti-ja-tr-osoite db-replica user tiedot))
      :siirry-toteuma
      (fn [user toteuma-id]
        (siirry-toteuma db-replica user toteuma-id))
      :hae-toteumien-reitit
      (fn [user tiedot]
        (hae-toteumien-reitit db-replica user tiedot))
      :hae-toteuman-liitteet
      (fn [user {:keys [urakka-id toteuma-id oikeus]}]
        (hae-toteuman-liitteet db user {:urakka-id  urakka-id
                                        :toteuma-id toteuma-id
                                        :oikeus     oikeus})))
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
      :poista-erilliskustannus
      :urakan-toteumien-toimenpiteet
      :maarien-toteutumien-toimenpiteiden-tehtavat
      :tallenna-toteuma
      :hae-maarien-toteuma
      :poista-toteuma
      :urakan-toteutuneet-muut-tyot
      :tallenna-muiden-toiden-toteuma
      :tallenna-toteuma-ja-toteumamateriaalit
      :hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
      :hae-kokonaishintaisen-toteuman-tiedot
      :urakan-varustetoteumat
      :hae-toteuman-reitti-ja-tr-osoite
      :siirry-toteuma
      :tallenna-varustetoteuma
      :hae-toteumien-reitit
      :hae-toteuman-liitteet)
    this))
