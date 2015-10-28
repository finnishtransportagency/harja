(ns harja.palvelin.palvelut.materiaalit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.materiaalit :as q]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn hae-materiaalikoodit [db]
  (into []
        (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
        (q/hae-materiaalikoodit-ilman-talvisuolaa db)))

(defn hae-urakan-materiaalit [db user urakka-id]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(if (:id (:pohjavesialue %))
                      %
                      (dissoc % :pohjavesialue)))
              (map #(assoc % :maara (double (:maara %))))
              (map #(assoc % :kokonaismaara (if (:kokonaismaara %) (double (:kokonaismaara %)) 0))))
        (let [tulos (q/hae-urakan-materiaalit db urakka-id)]
          (log/debug "HAETAAN URAKAN MATERIAALIT")
          tulos)))

(defn hae-urakassa-kaytetyt-materiaalit
  [db user urakka-id hk-alkanut hk-paattynyt sopimus]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(assoc % :maara (if (:maara %) (double (:maara %)) 0)))
              (map #(assoc % :kokonaismaara (if (:kokonaismaara %) (double (:kokonaismaara %)) 0))))
        (q/hae-urakassa-kaytetyt-materiaalit db (konv/sql-date hk-alkanut) (konv/sql-date hk-paattynyt) sopimus)))

(defn hae-urakan-toteumat-materiaalille
  [db user urakka-id materiaali-id hoitokausi sopimus]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(if (:id (:pohjavesialue %))
                     %
                     (dissoc % :pohjavesialue)))
              (map #(assoc-in % [:toteuma :maara] (when (:maara (:toteuma %)) (double (:maara (:toteuma %)))))))
        (let [tulos (q/hae-urakan-toteumat-materiaalille db
                                                         sopimus
                                                         materiaali-id
                                                         (konv/sql-date (first hoitokausi)) (konv/sql-date (second hoitokausi)))]
          (log/debug "HAETAAN URAKAN TOTEUMAT MATERIAALEILLE ("sopimus materiaali-id")")
          tulos)))

(defn hae-toteuman-materiaalitiedot
  [db user urakka-id toteuma-id]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [mankeloitava (into []
                           (comp (map konv/alaviiva->rakenne)
                                 (map #(assoc-in % [:toteumamateriaali :maara] (double (:maara (:toteumamateriaali %))))))
                           (q/hae-toteuman-materiaalitiedot db toteuma-id urakka-id))
        tulos (assoc
                (first (map :toteuma mankeloitava))
                :toteumamateriaalit (into [] (map :toteumamateriaali mankeloitava)))]
    (log/debug "Hae toteuman materiaalitiedot:")
    tulos))


(defn tallenna-urakan-materiaalit [db user {:keys [urakka-id sopimus-id hoitokausi hoitokaudet tulevat-hoitokaudet-mukana?  materiaalit] :as tiedot}]
  (roolit/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka-id)
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

      (doseq [[hoitokausi materiaalit] (ryhmittele materiaalit)]
        (log/debug "PÄIVITETÄÄN saadut materiaalit")
        (log/debug "Päivitetäänkö myös tulevilta? " tulevat-hoitokaudet-mukana?)

        (let [vanhat-materiaalit (get vanhat-materiaalit hoitokausi)
              materiaali-avain (juxt (comp :id :materiaali) (comp :id :pohjavesialue))
              materiaalit-kannassa (into {}
                                         (map (juxt materiaali-avain identity)
                                              vanhat-materiaalit))
              materiaalit-sisaan (into #{} (map materiaali-avain materiaalit))
              ]

          ;; Käydään läpi poistot
          ;; Poistetaan kaikki pohjavesialueen materiaalit, joita ei enää ole
          (doseq [{:keys [alkupvm loppupvm materiaali pohjavesialue]} (filter :poistettu materiaalit)
                  :when pohjavesialue]
            (q/poista-pohjavesialueen-materiaalinkaytto! c (:id user)
                                                  urakka-id sopimus-id
                                                  (konv/sql-date alkupvm) (konv/sql-date loppupvm)
                                                  (:id materiaali) (:id pohjavesialue)))

          ;; Muille materiaaleille, poistetaan jos ei ole enää sisääntulevissa
          (doseq [[avain {id :id}] materiaalit-kannassa
                  :when (not (materiaalit-sisaan avain))]
            (log/debug "ID " id " poistetaan, sitä ei enää ole sisääntulevissa")
            (q/poista-materiaalinkaytto-id! c (:id user) id))


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
                        (q/paivita-materiaalinkaytto-maara! c (:id user) (:maara materiaali) (:id materiaali-kannassa))
                        )))

              (let [{:keys [alkupvm loppupvm maara materiaali pohjavesialue]} materiaali]
                (log/debug "TÄYSIN UUSI MATSKU: " alkupvm loppupvm maara materiaali pohjavesialue)
                (q/luo-materiaalinkaytto<! c (konv/sql-date alkupvm) (konv/sql-date loppupvm) maara (:id materiaali)
                                    urakka-id sopimus-id (:id pohjavesialue) (:id user)))))))


      ;; Ihan lopuksi haetaan koko urakan materiaalit uusiksi
      (hae-urakan-materiaalit c user urakka-id))))

(defn tallenna-toteuma-materiaaleja!
  [db user urakka-id toteumamateriaalit hoitokausi sopimus]
  "Tallentaa toteuma-materiaaleja (yhden tai useamman), ja palauttaa urakassa käytetyt materiaalit jos
  hoitokausi on annettu.
  * Jos tehdään vain poistoja, on parempi käyttää poista-toteuma-materiaali! funktiota
  * Jos tähän funktioon tehdään muutoksia, pitäisi muutokset tehdä myös
  toteumat/tallenna-toteuma-ja-toteumamateriaalit funktioon (todnäk)"
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus
                            urakka-id)
  (jdbc/with-db-transaction [c db]
                            (doseq [tm toteumamateriaalit]
                              ;; Positiivinen id = luodaan tai poistetaan toteuma-materiaali
                              (if (and (:id tm) (pos? (:id tm)))
                                (if (:poistettu tm)
                                  (do
                                    (log/debug "Poistetaan materiaalitoteuma " (:id tm))
                                    (q/poista-toteuma-materiaali! c (:id user) (:id tm)))
                                  (do
                                    (log/debug "Päivitä materiaalitoteuma "
                                              (:id tm)" ("(:materiaalikoodi tm)", "(:maara tm)"), toteumassa " (:toteuma tm))
                                    (q/paivita-toteuma-materiaali!
                                      c (:materiaalikoodi tm) (:maara tm) (:id user) (:toteuma tm) (:id tm))))
                                (do
                                  (log/debug "Luo uusi materiaalitoteuma ("(:materiaalikoodi tm)", "(:maara tm)") toteumalle " (:toteuma tm))
                                  (q/luo-toteuma-materiaali<! c (:toteuma tm) (:materiaalikoodi tm) (:maara tm) (:id user)))))
                            (when hoitokausi
                              (hae-urakassa-kaytetyt-materiaalit c user urakka-id (first hoitokausi) (second hoitokausi) sopimus))))

(defn poista-toteuma-materiaali!
  [db user tiedot]
  "Poistaa toteuma-materiaalin id:llä. Vaatii lisäksi urakan id:n oikeuksien tarkastamiseen.
  Id:n voi antaa taulukossa, jolloin poistetaan useampi kerralla.

  Palauttaa urakassa käytetyt materiaalit, koska kyselyä käytetään toteumat/materiaalit näkymässä."
  [db user tiedot]
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus
                            (:urakka tiedot))
  (jdbc/with-db-transaction [c db]
                            (q/poista-toteuma-materiaali! c (:id user) (:id tiedot))
                            (when (:hk-alku tiedot)
                              (hae-urakassa-kaytetyt-materiaalit
                                c user (:urakka tiedot) (:hk-alku tiedot) (:hk-loppu tiedot) (:sopimus tiedot)))))


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
                      :poista-toteuma-materiaali!
                      (fn [user tiedot]
                        (poista-toteuma-materiaali! (:db this) user tiedot)))
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
                      :tallenna-urakan-materiaalit
                      (fn [user tiedot]
                        (tallenna-urakan-materiaalit (:db this) user tiedot)))
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

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-materiaalikoodit
                     :hae-urakan-materiaalit
                     :tallenna-urakan-materiaalit
                     :hae-urakan-toteumat-materiaalille
                     :hae-toteuman-materiaalitiedot
                     :hae-urakassa-kaytetyt-materiaalit
                     :poista-toteuma-materiaali!
                     :tallenna-toteuma-materiaaleja!)

    this))
