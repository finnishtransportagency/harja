(ns harja.palvelin.palvelut.materiaalit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.materiaalit :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.geo :as geo]
            [harja.palvelin.palvelut.toteumat-tarkistukset :as tarkistukset]
            [harja.pvm :as pvm]
            [clj-time.coerce :as tc]
            [clojure.string :as str]))

(defn hae-materiaalikoodit [db]
  (oikeudet/ei-oikeustarkistusta!)
  (into []
        (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
        (q/hae-materiaalikoodit-ilman-talvisuolaa db)))

(defn hae-urakan-materiaalit [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-materiaalit user urakka-id)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(assoc % :maara (double (:maara %)))))
        (let [tulos (q/hae-urakan-materiaalit db urakka-id)]
          (log/debug "HAETAAN URAKAN MATERIAALIT")
          tulos)))

(defn hae-urakassa-kaytetyt-materiaalit
  [db user urakka-id hk-alkanut hk-paattynyt sopimus]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-materiaalit user urakka-id)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(assoc % :maara (some-> % :maara double)))
              (map #(assoc % :kokonaismaara (if (:kokonaismaara %) (double (:kokonaismaara %)) 0))))
        (q/hae-urakassa-kaytetyt-materiaalit db (konv/sql-date hk-alkanut) (konv/sql-date hk-paattynyt) sopimus)))

(defn hae-urakan-toteumat-materiaalille
  [db user urakka-id materiaali-id [alku loppu] sopimus]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-materiaalit user urakka-id)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(assoc-in % [:toteuma :maara] (when (:maara (:toteuma %)) (double (:maara (:toteuma %)))))))
        (let [tulos (q/hae-urakan-toteumat-materiaalille db
                                                         sopimus
                                                         materiaali-id
                                                         (konv/sql-date alku)
                                                         (konv/sql-date loppu))]
          (log/debug "HAETAAN URAKAN TOTEUMAT MATERIAALEILLE (" sopimus materiaali-id ")")
          tulos)))

(defn hae-toteuman-materiaalitiedot
  [db user urakka-id toteuma-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-materiaalit user urakka-id)
  (let [mankeloitava (into []
                           (comp (map konv/alaviiva->rakenne)
                                 (map #(assoc-in % [:toteumamateriaali :maara] (double (:maara (:toteumamateriaali %))))))
                           (q/hae-toteuman-materiaalitiedot db toteuma-id urakka-id))
        tulos (assoc
                (first (map :toteuma mankeloitava))
                :toteumamateriaalit (into [] (map :toteumamateriaali mankeloitava)))]
    (log/debug "Hae toteuman materiaalitiedot:")
    tulos))


(defn poista-urakan-materiaalit
  [hoitokaudet hoitokausi tulevat-hoitokaudet-mukana? urakka-id sopimus-id user c]
  (if tulevat-hoitokaudet-mukana?
    (do
      (doseq [i hoitokaudet]
        (if (or
              (t/equal? (c/from-date (first i)) (c/from-date (first hoitokausi)))
              (t/after? (c/from-date (first i)) (c/from-date (first hoitokausi))))
          (do (log/debug "Poistetaan materiaalit hoitokaudelta: " (pr-str i))
              (q/poista-urakan-materiaalinkaytto! c (:id user)
                                                  urakka-id sopimus-id
                                                  (konv/sql-date (first i)) (konv/sql-date (second i)))))))
    (do
      (log/debug "Poistetaan materiaalit hoitokaudelta: " (pr-str hoitokausi))
      (q/poista-urakan-materiaalinkaytto! c (:id user)
                                          urakka-id sopimus-id
                                          (konv/sql-date (first hoitokausi)) (konv/sql-date (second hoitokausi))))))

(defn poista-materiaalit-joita-ei-sisaan-tulevissa
  [materiaalit materiaalit-sisaan user c]
  (doseq [[avain {id :id}] materiaalit
          :when (not (materiaalit-sisaan avain))]
    (log/debug "ID " id " poistetaan, sitä ei enää ole sisääntulevissa")
    (q/poista-materiaalinkaytto-id! c (:id user) id)))

(defn tallenna-suunnitellut-materiaalit [db user {:keys [urakka-id sopimus-id hoitokausi hoitokaudet tulevat-hoitokaudet-mukana? materiaalit] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-materiaalit user urakka-id)
  (log/debug "MATERIAALIT PÄIVITETTÄVÄKSI: " tiedot)
  (jdbc/with-db-transaction [c db]
    (let [ryhmittele #(group-by (juxt :alkupvm :loppupvm) %)
          vanhat-materiaalit (ryhmittele
                               (filter #(= sopimus-id (:sopimus %))
                                       (hae-urakan-materiaalit c user urakka-id)))]
      ;; Ei materiaaleja, poista kaikki urakan materiaalit
      (when (empty? materiaalit)
        (log/debug "YHTÄÄN MATERIAALIA EI SAATU, poistetaan materiaalit valitulta hoitokaudelta")
        (log/debug "Poistetaanko myös tulevilta? " tulevat-hoitokaudet-mukana?)
        (poista-urakan-materiaalit hoitokaudet hoitokausi tulevat-hoitokaudet-mukana? urakka-id sopimus-id user c))

      (doseq [[hoitokausi materiaalit] (ryhmittele materiaalit)]
        (log/debug "PÄIVITETÄÄN saadut materiaalit")
        (log/debug "Päivitetäänkö myös tulevilta? " tulevat-hoitokaudet-mukana?)

        (let [vanhat-materiaalit (get vanhat-materiaalit hoitokausi)
              materiaali-avain (juxt (comp :id :materiaali) (comp :id :pohjavesialue))
              materiaalit-kannassa (into {}
                                         (map (juxt materiaali-avain identity)
                                              vanhat-materiaalit))
              materiaalit-sisaan (into #{} (map materiaali-avain materiaalit))]

          ;; Muille materiaaleille, poistetaan jos ei ole enää sisääntulevissa
          (poista-materiaalit-joita-ei-sisaan-tulevissa materiaalit-kannassa materiaalit-sisaan user c)


          ;; Käydään läpi frontin lähettämät uudet materiaalit
          ;; Jos materiaali on kannassa, päivitetään sen määrä tarvittaessa
          ;; Jos materiaali ei ole kannassa, syötetään se uutena
          (doseq [materiaali materiaalit
                  :when (not (:poistettu materiaali))
                  :let [avain (materiaali-avain materiaali)]]
            (if-let [materiaali-kannassa (materiaalit-kannassa avain)]
              ;; Materiaali on jo kannassa, päivitä, jos muuttunut
              (do (log/debug "TÄMÄ MATSKU ON KANNASSA: " avain)
                  (if (== (:maara materiaali) (:maara materiaali-kannassa))
                    (do (log/debug "Ei muutosta määrään, ei päivitetä."))
                    (do (log/debug "Määrä muuttunut " (:maara materiaali-kannassa) " => " (:maara materiaali) ", päivitetään!")
                        (q/paivita-materiaalinkaytto-maara! c (:id user) (:maara materiaali) (:id materiaali-kannassa)))))

              (let [{:keys [alkupvm loppupvm maara materiaali]} materiaali]
                (log/debug "TÄYSIN UUSI MATSKU: " alkupvm loppupvm maara materiaali)
                (q/luo-materiaalinkaytto<! c (konv/sql-date alkupvm) (konv/sql-date loppupvm) maara (:id materiaali)
                                           urakka-id sopimus-id (:id user)))))))


      ;; Ihan lopuksi haetaan koko urakan materiaalit uusiksi
      (hae-urakan-materiaalit c user urakka-id))))

(defn tallenna-toteuma-materiaaleja!
  "Tallentaa toteuma-materiaaleja (yhden tai useamman), ja palauttaa urakassa käytetyt materiaalit jos
  hoitokausi on annettu.
  * Jos tehdään vain poistoja, on parempi käyttää poista-toteuma-materiaali! funktiota
  * Jos tähän funktioon tehdään muutoksia, pitäisi muutokset tehdä myös
  toteumat/tallenna-toteuma-ja-toteumamateriaalit funktioon (todnäk)"
  [db user urakka-id toteumamateriaalit hoitokausi sopimus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-materiaalit user urakka-id)
  (jdbc/with-db-transaction [c db]
    (let [sopimus-idt (map :id (sopimukset-q/hae-urakan-sopimus-idt c {:urakka_id urakka-id}))]
      (doseq [tm toteumamateriaalit]
        (tarkistukset/vaadi-toteuma-kuuluu-urakkaan c (:toteuma tm) urakka-id)
        (tarkistukset/vaadi-toteuma-ei-jarjestelman-luoma c (:toteuma tm))
        ;; Positiivinen id = luodaan tai poistetaan toteuma-materiaali
        (if (and (:id tm) (pos? (:id tm)))
          (do
            (if (:poistettu tm)
              (do
                (log/debug "Poistetaan materiaalitoteuma " (:id tm))
                (q/poista-toteuma-materiaali! c (:id user) (:id tm)))
              (do
                (log/debug "Päivitä materiaalitoteuma "
                           (:id tm) " (" (:materiaalikoodi tm) ", " (:maara tm) "), toteumassa " (:toteuma tm))
                (q/paivita-toteuma-materiaali!
                  c (:materiaalikoodi tm) (:maara tm) (:id user) (:toteuma tm) (:id tm)))))
          (do
            (log/debug "Luo uusi materiaalitoteuma (" (:materiaalikoodi tm) ", " (:maara tm) ") toteumalle " (:toteuma tm))
            (q/luo-toteuma-materiaali<! c (:toteuma tm) (:materiaalikoodi tm)
                                        (:maara tm) (:id user) urakka-id)))

        ;; Päivitä toteuman päivän mukainen materiaalin käyttö
        (doseq [sopimus-id sopimus-idt]
          (q/paivita-sopimuksen-materiaalin-kaytto-toteumapvm c sopimus-id
                                                              (:toteuma tm))))))

  (when hoitokausi
    (hae-urakassa-kaytetyt-materiaalit db user urakka-id (first hoitokausi) (second hoitokausi)
                                       sopimus)))

(defn poista-toteuma-materiaali!
  "Poistaa toteuma-materiaalin id:llä. Vaatii lisäksi urakan id:n oikeuksien tarkastamiseen.
  Id:n voi antaa taulukossa, jolloin poistetaan useampi kerralla.

  Palauttaa urakassa käytetyt materiaalit, koska kyselyä käytetään toteumat/materiaalit näkymässä."
  [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-materiaalit user (:urakka tiedot))
  (jdbc/with-db-transaction
    [db db]
    (when (:tmid tiedot)
      (q/poista-toteuma-materiaali! db (:id user) (:tmid tiedot)))
    (when (:tid tiedot)
      (toteumat-q/poista-toteuma! db (:id user) (:tid tiedot)))
    (when (:hk-alku tiedot)
      (hae-urakassa-kaytetyt-materiaalit
        db user (:urakka tiedot) (:hk-alku tiedot) (:hk-loppu tiedot) (:sopimus tiedot)))))

(defn hae-suolatoteumien-tarkat-tiedot
  [db user {:keys [toteumaidt materiaali-id urakka-id]}]
  (log/debug "hae-suolatoteumien-tarkat-tiedot " toteumaidt " materiaali " materiaali-id " urakkaid " urakka-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-materiaalit user urakka-id)
  (let [toteumat (into []
                       (comp
                         (map konv/alaviiva->rakenne))
                       (q/hae-suolatoteumien-tarkat-tiedot-materiaalille db {:toteumaidt toteumaidt
                                                                             :materiaali_id materiaali-id}))]
    toteumat))

(defn hae-suolatoteumat [db user {:keys [urakka-id alkupvm loppupvm]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-materiaalit user urakka-id)
  (log/debug "kutsutaan hae-suolatoteumat: " {:urakka urakka-id
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm})
  (let [toteumat (into []
                       (comp
                         (map konv/alaviiva->rakenne)
                         (map #(konv/array->vec % :toteumaidt)))
                       (q/hae-suolatoteumien-summatiedot db {:urakka urakka-id
                                                             :alkupvm alkupvm
                                                             :loppupvm loppupvm}))]
    toteumat))

(defn hae-suolatoteumat-tr-valille [db user {:keys [urakka-id tie alkuosa alkuet loppuosa loppuet alkupvm loppupvm]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-materiaalit user urakka-id)
  (into []
        (comp
         (map konv/alaviiva->rakenne)
         (map #(konv/array->vec % :toteumaidt)))
        (q/hae-suolatoteumat-tr-valille db {:urakka urakka-id
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm
                                            :threshold 50
                                            :tie tie
                                            :alkuosa alkuosa
                                            :alkuet alkuet
                                            :loppuosa loppuosa
                                            :loppuet loppuet})))

(defn hae-suolamateriaalit [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (into []
        (q/hae-suolamateriaalit db)))

(defn luo-suolatoteuma [db user urakka-id sopimus-id toteuma]
  (let [toteuman-id (toteumat-q/luo-uusi-toteuma db
                                                 {:urakka urakka-id
                                                  :sopimus sopimus-id
                                                  :alkanut (:pvm toteuma)
                                                  :paattynyt (:pvm toteuma)
                                                  :tyyppi "kokonaishintainen"
                                                  :kayttaja (:id user)
                                                  :suorittaja ""
                                                  :ytunnus ""
                                                  :lisatieto (:lisatieto toteuma)
                                                  :ulkoinen_id nil
                                                  :reitti nil
                                                  :numero nil
                                                  :alkuosa nil
                                                  :alkuetaisyys nil
                                                  :loppuosa nil
                                                  :loppuetaisyys nil
                                                  :lahde "harja-ui"
                                                  :tyokonetyyppi nil
                                                  :tyokonetunniste nil
                                                  :tyokoneen-lisatieto nil})]
    (toteumat-q/luo-toteuma-materiaali<! db toteuman-id
                                         (:id (:materiaali toteuma))
                                         (:maara toteuma)
                                         (:id user)
                                         urakka-id)))

(defn tallenna-kasinsyotetty-toteuma [db user {:keys [urakka-id sopimus-id toteuma]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (jdbc/with-db-transaction [db db]
    (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db (:tid toteuma) urakka-id)
    (luo-suolatoteuma db user urakka-id sopimus-id toteuma)
    (let [nyt (konv/sql-date (pvm/nyt))]
      (toteumat-q/paivita-toteuma<! db
                                    {:alkanut (:pvm nyt)
                                     :paattynyt (:pvm nyt)
                                     :tyyppi "kokonaishintainen"
                                     :kayttaja (:id user)
                                     :suorittaja (:suorittajan-nimi toteuma)
                                     :ytunnus (:suorittajan-ytunnus toteuma)
                                     :lisatieto (:lisatieto toteuma)
                                     :numero (:numero (:tierekisteriosoite toteuma))
                                     :alkuosa (:alkuosa (:tierekisteriosoite toteuma))
                                     :alkuetaisyys nil
                                     :loppuosa nil
                                     :loppuetaisyys nil
                                     :id (:tid toteuma)
                                     :urakka urakka-id}))))

;;(defn tallenna-suolatoteumat [db user {:keys [urakka-id sopimus-id toteumat]}]
(defn tallenna-suolatoteumat [db user {:keys [urakka-id sopimus-id toteumat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [urakan-sopimus-idt (map :id (sopimukset-q/hae-urakan-sopimus-idt db {:urakka_id urakka-id}))]
      (doseq [toteuma toteumat]
        (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db (:tid toteuma) urakka-id)
        (log/debug "TALLENNA SUOLATOTEUMA: " toteuma)
        (if-not (id-olemassa? (:tid toteuma))
          (luo-suolatoteuma db user urakka-id sopimus-id toteuma)
          (let [tmid (:tmid toteuma)]
            (if (:poistettu toteuma)
              (do
                (log/debug "poista toteuma materiaali id: " tmid)
                (poista-toteuma-materiaali! db user toteuma))
              (do
                (log/debug "päivitä toteuma materiaali id: " tmid)
                (toteumat-q/paivita-toteuma<! db
                                              {:alkanut (:pvm toteuma)
                                               :paattynyt (:pvm toteuma)
                                               :tyyppi "kokonaishintainen"
                                               :kayttaja (:id user)
                                               :suorittaja (:suorittajan-nimi toteuma)
                                               :ytunnus (:suorittajan-ytunnus toteuma)
                                               :lisatieto (:lisatieto toteuma)
                                               :numero nil
                                               :alkuosa nil
                                               :alkuetaisyys nil
                                               :loppuosa nil
                                               :loppuetaisyys nil
                                               :id (:tid toteuma)
                                               :urakka urakka-id})
                (when (:reitti toteuma) (toteumat-q/paivita-toteuman-reitti! db
                                                                             {:reitti (geo/geometry (geo/clj->pg (:reitti toteuma)))
                                                                              :id (:tid toteuma)}))
                (toteumat-q/paivita-toteuma-materiaali! db (:id (:materiaali toteuma))
                                                        (:maara toteuma)
                                                        (:id user)
                                                        (:tmid toteuma)
                                                        urakka-id)))))
        (doseq [sopimus-id urakan-sopimus-idt]
          (materiaalit/paivita-sopimuksen-materiaalin-kaytto db {:sopimus sopimus-id
                                                                 :alkupvm (:pvm toteuma)}))))
    true))

(defrecord Materiaalit []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-materiaalikoodit
                      (fn [user]
                        (hae-materiaalikoodit (:db this))))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-materiaalit
                      (fn [user urakka-id]
                        (hae-urakan-materiaalit (:db this) user urakka-id)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-toteumat-materiaalille
                      (fn [user tiedot]
                        (hae-urakan-toteumat-materiaalille
                          (:db this) user (:urakka-id tiedot) (:materiaali-id tiedot)
                          (:hoitokausi tiedot) (:sopimus tiedot))))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-toteuman-materiaalitiedot
                      (fn [user tiedot]
                        (hae-toteuman-materiaalitiedot
                          (:db this) user (:urakka-id tiedot) (:toteuma-id tiedot))))
    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-suunnitellut-materiaalit
                      (fn [user tiedot]
                        (tallenna-suunnitellut-materiaalit (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-toteuma-materiaaleja!
                      (fn [user tiedot]
                        (tallenna-toteuma-materiaaleja!
                          (:db this) user
                          (:urakka-id tiedot)
                          (:toteumamateriaalit tiedot)
                          (:hoitokausi tiedot)
                          (:sopimus tiedot))))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakassa-kaytetyt-materiaalit
                      (fn [user tiedot]
                        (hae-urakassa-kaytetyt-materiaalit (:db this) user
                                                           (:urakka-id tiedot)
                                                           (:hk-alku tiedot)
                                                           (:hk-loppu tiedot)
                                                           (:sopimus tiedot))))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-suolatoteumat
                      (fn [user tiedot]
                        (log/debug "hae-suolatoteumat: tiedot" tiedot)
                        (hae-suolatoteumat (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-suolatoteumat-tr-valille
                      (fn [user tiedot]
                        (log/debug "hae-suolatoteumat-tr-valille: tiedot" tiedot)
                        (hae-suolatoteumat-tr-valille (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-suolatoteumien-tarkat-tiedot
                      (fn [user tiedot]
                        (log/debug "hae-suolatoteumien-tarkat-tiedot: " tiedot)
                        (hae-suolatoteumien-tarkat-tiedot (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-suolamateriaalit
                      (fn [user]
                        (hae-suolamateriaalit (:db this) user)))
    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-suolatoteumat
                      (fn [user tiedot]
                        (tallenna-suolatoteumat (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-kasinsyotetty-suolatoteuma
                      (fn [user tiedot]
                        (tallenna-kasinsyotetty-toteuma (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-materiaalikoodit
                     :hae-urakan-materiaalit
                     :tallenna-suunnitellut-materiaalit
                     :hae-urakan-toteumat-materiaalille
                     :hae-toteuman-materiaalitiedot
                     :hae-urakassa-kaytetyt-materiaalit
                     :tallenna-toteuma-materiaaleja!
                     :hae-suolatoteumat
                     :hae-suolatoteumien-tarkat-tiedot
                     :hae-suolamateriaalit
                     :tallenna-suolatoteumat
                     :tallenna-kasinsyotetty-suolatoteuma)

    this))
