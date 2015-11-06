(ns harja.palvelin.palvelut.toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.roolit :as roolit]
            [clojure.java.jdbc :as jdbc]

            [harja.kyselyt.toteumat :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]
            [harja.kyselyt.muutoshintaiset-tyot :as mht-q]
            [harja.kyselyt.kayttajat :as kayttajat-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]))

(defn annettu? [p]
  (if (nil? p)
    false
    (do
      (if (string? p)
        (not (empty? p))

        (if (vector? p)
          (do
            (if (empty? p)
              false
              (some true? (map annettu? p))))

          (if (map? p)
            (do
              (if (empty? p)
                false
                (some true? (map #(annettu? (val %)) p))))

            true))))))

(def toteuma-xf
  (comp (map #(-> %
                  (konv/array->vec :tehtavat)
                  (konv/array->vec :materiaalit)))))

(def muunna-desimaaliluvut-xf
  (map #(-> %
            (assoc :maara
                   (or (some-> % :maara double) 0)))))

(def toteumien-tehtavat->map-xf
  (map #(-> %
            (assoc :tehtavat
                   (mapv (fn [tehtava]
                           (let [splitattu (str/split tehtava #"\^")]
                             {:tehtava-id (Integer/parseInt (first splitattu))
                              :tpk-id     (Integer/parseInt (second splitattu))
                              :nimi       (get splitattu 2)
                              :maara      (Double/parseDouble (get splitattu 3))
                              }))
                         (:tehtavat %))))))

(defn toteuman-parametrit [toteuma kayttaja]
  [(:urakka-id toteuma) (:sopimus-id toteuma)
   (konv/sql-timestamp (:alkanut toteuma)) (konv/sql-timestamp (:paattynyt toteuma))
   (name (:tyyppi toteuma)) (:id kayttaja)
   (:suorittajan-nimi toteuma) (:suorittajan-ytunnus toteuma) (:lisatieto toteuma) nil nil])

(defn toteumatehtavan-parametrit [toteuma kayttaja]
  [(get-in toteuma [:tehtava :toimenpidekoodi]) (get-in toteuma [:tehtava :maara]) (:id kayttaja)
   (get-in toteuma [:tehtava :paivanhinta])])


(defn hae-urakan-toteumat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (comp
          toteuma-xf
          toteumien-tehtavat->map-xf)
        (q/hae-urakan-toteumat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm) (name tyyppi))))

(defn hae-urakan-toteuma [db user {:keys [urakka-id toteuma-id]}]
  (log/debug "Haetaan urakan toteuma id:llä: " toteuma-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [toteuma (into []
                      (comp
                        toteuma-xf
                        toteumien-tehtavat->map-xf
                        (harja.geo/muunna-pg-tulokset :reittipiste_sijainti)
                        (map konv/alaviiva->rakenne))
                      (q/hae-urakan-toteuma db urakka-id toteuma-id))
        kasitelty-toteuma (first
                            (konv/sarakkeet-vektoriin
                              toteuma
                              {:reittipiste :reittipisteet}))]
    (log/debug "Käsitelty toteuma: " (pr-str kasitelty-toteuma))
    kasitelty-toteuma))

(defn hae-urakan-toteumien-tehtavien-summat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi]}]
  (log/debug "Haetaan urakan toteuman tehtävien summat: " urakka-id sopimus-id alkupvm loppupvm tyyppi)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        muunna-desimaaliluvut-xf
        (q/hae-toteumien-tehtavien-summat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm) (name tyyppi))))

(defn hae-urakan-toteutuneet-tehtavat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi]}]
  (log/debug "Haetaan urakan toteutuneet tehtävät: " urakka-id sopimus-id alkupvm loppupvm tyyppi)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [toteutuneet-tehtavat (into []
                                   muunna-desimaaliluvut-xf
                                   (q/hae-urakan-ja-sopimuksen-toteutuneet-tehtavat db urakka-id sopimus-id (konv/sql-timestamp alkupvm) (konv/sql-timestamp loppupvm) (name tyyppi)))]
    (log/debug "Haetty urakan toteutuneet tehtävät: " toteutuneet-tehtavat)
    toteutuneet-tehtavat))

(defn hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi toimenpidekoodi]}]
  (log/debug "Haetaan urakan toteutuneet tehtävät tyypillä ja toimenpidekoodilla: " urakka-id sopimus-id alkupvm loppupvm tyyppi toimenpidekoodi)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        muunna-desimaaliluvut-xf
        (q/hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db urakka-id sopimus-id (konv/sql-timestamp alkupvm) (konv/sql-timestamp loppupvm) (name tyyppi) toimenpidekoodi)))

(defn hae-toteumat-historiakuvaan [db user {:keys [hallintayksikko urakka alku loppu toimenpidekoodit alue]}]
  (log/debug "Haetaan toteumia historiakuvaan.")
  (log/debug (pr-str hallintayksikko urakka))
  (log/debug (pr-str alue))
  (log/debug (pr-str toimenpidekoodit))

  (when urakka (roolit/vaadi-lukuoikeus-urakkaan user urakka))
  (jdbc/with-db-transaction [db db]
    (let [urakka-idt (if-not (nil? urakka)
                       (if (vector? urakka) urakka [urakka])

                       (mapv :urakka_id (kayttajat-q/hae-kayttajan-urakka-roolit db (:id user))))
          ;; Tuloksia ei rajata urakalla, jos urakkaa ei ole valittu ja käyttäjä on järjestelmavastuuhenkilö
          rajaa-urakalla? (not (and (nil? urakka) (get (:roolit user) "jarjestelmavastuuhenkilo")))
          hallintayksikko_annettu (annettu? hallintayksikko)]

      ;; On mahdollista, että käyttäjä on joko järjestelmävastuuhenkilö (= Pääsy kaikkiin urakoihin), tai
      ;; urakoitsija tms. joka pääsee vain tiettyihin urakoihin.
      ;; On myös mahdollista, että käyttäjä valitsee käyttöliittymän kautta haettavan urakan, tai hakee
      ;; kaikista tälle liitetyistä urakoista.
      ;; - JOS käyttäjä on valinnut urakan, haetaan vain tästä urakasta (lista jossa on yksi elementti).
      ;;   rajaa-urakalla? on tällöin true
      ;; - JOS käyttäjä EI ole valinnut urakkaa JA tämä on 'normaali käyttäjä', ollaan haettu listaan
      ;;   kaikki käyttäjälle kuuluvat urakat. Tällöin kysely tehdään jokaiselle listassa olevalle urakalle
      ;; - JOS käyttäjä EI ole valinnut urakkaa JA tämä on JVH, 'rajaa-urakalla?' on FALSE, urakka-idt on
      ;;   tyhjä lista ja palautetaan "kaikki asiat".
      (let [kyselyn_tulos (if-not (empty? urakka-idt)
                            (do
                              (log/debug "Haetaan urakoista " (pr-str urakka-idt) " (" (pr-str rajaa-urakalla?) ")")
                              (apply (comp vec flatten merge)
                                     (for [urakka-id urakka-idt]
                                       (q/hae-toteumat-historiakuvaan db (konv/sql-date alku) (konv/sql-date loppu)
                                                                      toimenpidekoodit (:xmin alue) (:ymin alue) (:xmax alue) (:ymax alue)
                                                                      urakka-id rajaa-urakalla?
                                                                      hallintayksikko_annettu hallintayksikko))))

                            (when-not rajaa-urakalla?
                              (log/debug "Hakua ei rajata urakalla - käyttäjä on järjestelmävastuuhenkilö")
                              (q/hae-toteumat-historiakuvaan db (konv/sql-date alku) (konv/sql-date loppu)
                                                             toimenpidekoodit (:xmin alue) (:ymin alue) (:xmax alue) (:ymax alue)
                                                             0 rajaa-urakalla?
                                                             hallintayksikko_annettu hallintayksikko)))

            _ (log/debug (pr-str (count kyselyn_tulos)))
            mankeloitava (into []
                               (comp
                                 (harja.geo/muunna-pg-tulokset :reittipiste_sijainti)
                                 (map konv/alaviiva->rakenne)
                                 (map #(assoc % :tyyppi :toteuma)))
                               kyselyn_tulos)
            tulos (konv/sarakkeet-vektoriin
                    mankeloitava
                    {:tehtava     :tehtavat
                     :materiaali  :materiaalit
                     :reittipiste :reittipisteet})]
        (log/debug (pr-str "Löydettiin " (count tulos) " toteumaa historiakuvaan."))

        tulos))))

(defn hae-urakan-toteuma-paivat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteumapäivän: " urakka-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into #{}
        (map :paiva)
        (q/hae-urakan-toteuma-paivat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))


(defn hae-urakan-tehtavat [db user urakka-id]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (q/hae-urakan-tehtavat db urakka-id)))

(defn kasittele-toteumatehtava [c user toteuma tehtava]
  (if (and (:tehtava-id tehtava) (pos? (:tehtava-id tehtava)))
    (do
      (if (:poistettu tehtava)
        (do (log/debug "Poistetaan tehtävä: " (pr-str tehtava))
            (q/poista-toteuman-tehtava! c (:tehtava-id tehtava)))
        (do (log/debug "Pävitetään tehtävä: " (pr-str tehtava))
            (q/paivita-toteuman-tehtava! c (:toimenpidekoodi tehtava) (:maara tehtava) (or (:poistettu tehtava) false)
                                         (or (:paivanhinta tehtava) nil)
                                         (:tehtava-id tehtava)))))
    (do
      (when (not (:poistettu tehtava))
        (log/debug "Luodaan uusi tehtävä.")
        (q/luo-tehtava<! c (:toteuma-id toteuma) (:toimenpidekoodi tehtava) (:maara tehtava) (:id user) nil)))))

(defn kasittele-toteuman-tehtavat [c user toteuma]
  (doseq [tehtava (:tehtavat toteuma)]
    (kasittele-toteumatehtava c user toteuma tehtava)))

(defn paivita-toteuma [c user toteuma]
  (q/paivita-toteuma! c (konv/sql-date (:alkanut toteuma)) (konv/sql-date (:paattynyt toteuma)) (:id user)
                      (:suorittajan-nimi toteuma) (:suorittajan-ytunnus toteuma) (:lisatieto toteuma) nil
                      (:toteuma-id toteuma) (:urakka-id toteuma))
  (kasittele-toteuman-tehtavat c user toteuma)
  (:toteuma-id toteuma))

(defn luo-toteuma [c user toteuma]
  (let [toteuman-parametrit (into [] (concat [c] (toteuman-parametrit toteuma user)))
        uusi (apply q/luo-toteuma<! toteuman-parametrit)
        id (:id uusi)
        toteumatyyppi (name (:tyyppi toteuma))]
    (doseq [{:keys [toimenpidekoodi maara]} (:tehtavat toteuma)]
      (q/luo-tehtava<! c id toimenpidekoodi maara (:id user) nil)
      (q/merkitse-toteuman-maksuera-likaiseksi! c toteumatyyppi toimenpidekoodi))
    id))

(defn tallenna-toteuma-ja-yksikkohintaiset-tehtavat
  "Tallentaa toteuman. Palauttaa sen ja tehtävien summat."
  [db user toteuma]
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus (:urakka toteuma))
  (log/debug "Toteuman tallennus aloitettu. Payload: " (pr-str toteuma))
  (jdbc/with-db-transaction [c db]
    (let [id
          (if (:toteuma-id toteuma)
            (paivita-toteuma c user toteuma)
            (luo-toteuma c user toteuma))
          paivitetyt-summat
          (hae-urakan-toteumien-tehtavien-summat c user
                                                 {:urakka-id  (:urakka-id toteuma)
                                                  :sopimus-id (:sopimus-id toteuma)
                                                  :alkupvm    (konv/sql-timestamp (:hoitokausi-aloituspvm toteuma))
                                                  :loppupvm   (konv/sql-timestamp (:hoitokausi-lopetuspvm toteuma))
                                                  :tyyppi     (:tyyppi toteuma)})]
      {:toteuma          (assoc toteuma :toteuma-id id)
       :tehtavien-summat paivitetyt-summat})))

(defn paivita-yk-hint-toiden-tehtavat
  "Päivittää yksikköhintaisen töiden toteutuneet tehtävät. Palauttaa päivitetyt tehtävät sekä tehtävien summat"
  [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tyyppi tehtavat]}]
  (roolit/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo} urakka-id)
  (log/debug (str "Yksikköhintaisten töiden päivitys aloitettu. Payload: " (pr-str (into [] tehtavat))))

  (let [tehtavatidt (into #{} (map #(:tehtava_id %) tehtavat))]
    (jdbc/with-db-transaction [c db]
      (doall
        (for [tehtava tehtavat]
          (do
            (log/debug (str "Päivitetään saapunut tehtävä. id: " (:tehtava_id tehtava)))
            (q/paivita-toteuman-tehtava! c (:toimenpidekoodi tehtava) (:maara tehtava) (:poistettu tehtava) (:paivanhinta tehtava) (:tehtava_id tehtava)))))

      (log/debug "Merkitään tehtavien: " tehtavatidt " maksuerät likaisiksi")
      (q/merkitse-toteumatehtavien-maksuerat-likaisiksi! c tehtavatidt)))

  (let [paivitetyt-tehtavat (hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla db user
                                                                                {:urakka-id       urakka-id
                                                                                 :sopimus-id      sopimus-id
                                                                                 :alkupvm         alkupvm
                                                                                 :loppupvm        loppupvm
                                                                                 :tyyppi          tyyppi
                                                                                 :toimenpidekoodi (:toimenpidekoodi (first tehtavat))})
        paivitetyt-summat (hae-urakan-toteumien-tehtavien-summat db user
                                                                 {:urakka-id  urakka-id
                                                                  :sopimus-id sopimus-id
                                                                  :alkupvm    alkupvm
                                                                  :loppupvm   loppupvm
                                                                  :tyyppi     tyyppi})]
    (log/debug "Palautetaan päivittynyt data: " (pr-str paivitetyt-tehtavat))
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
    erilliskustannus-rahasumma-xf))

(defn hae-urakan-erilliskustannukset [db user {:keys [urakka-id alkupvm loppupvm]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        erilliskustannus-xf
        (q/listaa-urakan-hoitokauden-erilliskustannukset db urakka-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))

(defn tallenna-erilliskustannus [db user ek]
  (roolit/vaadi-rooli-urakassa user
                               roolit/toteumien-kirjaus
                               (:urakka-id ek))
  (jdbc/with-db-transaction [c db]
    (let [parametrit [c (:tyyppi ek) (:sopimus ek) (:toimenpideinstanssi ek)
                      (konv/sql-date (:pvm ek)) (:rahasumma ek) (:indeksin_nimi ek) (:lisatieto ek) (:id user)]]
      (if (not (:id ek))
        (apply q/luo-erilliskustannus<! parametrit)

        (apply q/paivita-erilliskustannus! (concat parametrit [(or (:poistettu ek) false) (:id ek)]))))
    (q/merkitse-toimenpideinstanssin-kustannussuunnitelma-likaiseksi! c (:toimenpideinstanssi ek))
    (hae-urakan-erilliskustannukset c user {:urakka-id (:urakka-id ek)
                                            :alkupvm   (:alkupvm ek)
                                            :loppupvm  (:loppupvm ek)})))


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
    (harja.geo/muunna-pg-tulokset :reittipiste_sijainti)
    muut-tyot-rahasumma-xf
    muut-tyot-maara-xf
    (map konv/alaviiva->rakenne)
    muut-tyot-tyyppi-xf))

(defn hae-urakan-muut-tyot [db user {:keys [urakka-id sopimus-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan muut työt: " urakka-id " ajalta " alkupvm "-" loppupvm)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (konv/sarakkeet-vektoriin
    (into []
          muut-tyot-xf
          (q/listaa-urakan-hoitokauden-toteumat-muut-tyot db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm)))
    {:reittipiste :reittipisteet}
    #(get-in % [:toteuma :id])))

(defn paivita-muun-tyon-toteuma
  [c user toteuma]
  (log/debug "Päivitä toteuma" toteuma)
  (if (:poistettu toteuma)
    (let [params [c (:id user) (get-in toteuma [:toteuma :id])]]
      (log/debug "poista toteuma" (get-in toteuma [:toteuma :id]))
      (apply q/poista-toteuman-tehtavat! params)
      (apply q/poista-toteuma! params))
    (do (q/paivita-toteuma! c (konv/sql-date (:alkanut toteuma)) (konv/sql-date (:paattynyt toteuma)) (:id user)
                            (:suorittajan-nimi toteuma) (:suorittajan-ytunnus toteuma) (:lisatieto toteuma) nil
                            (get-in toteuma [:toteuma :id]) (:urakka-id toteuma))
        (kasittele-toteumatehtava c user toteuma (assoc (:tehtava toteuma)
                                                   :tehtava-id (get-in toteuma [:tehtava :id]))))))

(defn luo-muun-tyon-toteuma
  [c user toteuma]
  (log/debug "Luodaan uusi toteuma" toteuma)
  (let [toteuman-parametrit (into [] (concat [c] (toteuman-parametrit toteuma user)))
        uusi (apply q/luo-toteuma<! toteuman-parametrit)
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
    (apply q/luo-tehtava<! toteumatehtavan-parametrit)
    (log/debug "Merkitään maksuera likaiseksi maksuerätyypin: " maksueratyyppi " toteumalle jonka toimenpidekoodi on: " toimenpidekoodi)
    (q/merkitse-toteuman-maksuera-likaiseksi! c maksueratyyppi toimenpidekoodi)
    true)
  )

(defn tallenna-muiden-toiden-toteuma
  [db user toteuma]
  (roolit/vaadi-rooli-urakassa user
                               #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo}
                               (:urakka-id toteuma))
  (jdbc/with-db-transaction [c db]
    (if (get-in toteuma [:tehtava :id])
      (paivita-muun-tyon-toteuma c user toteuma)
      (luo-muun-tyon-toteuma c user toteuma))
    ;; lisätään tarvittaessa hinta muutoshintainen_tyo tauluun
    (when (:uusi-muutoshintainen-tyo toteuma)
      (let [parametrit [c (:yksikko toteuma) (:yksikkohinta toteuma) (:id user)
                        (:urakka-id toteuma) (:sopimus-id toteuma) (get-in toteuma [:tehtava :toimenpidekoodi])
                        (konv/sql-date (:urakan-alkupvm toteuma))
                        (konv/sql-date (:urakan-loppupvm toteuma))]]
        (apply mht-q/lisaa-muutoshintainen-tyo<! parametrit)))
    (hae-urakan-muut-tyot c user
                          {:urakka-id  (:urakka-id toteuma)
                           :sopimus-id (:sopimus-id toteuma)
                           :alkupvm    (konv/sql-timestamp (:hoitokausi-aloituspvm toteuma))
                           :loppupvm   (konv/sql-timestamp (:hoitokausi-lopetuspvm toteuma))})))



(defn tallenna-toteuma-ja-toteumamateriaalit
  "Tallentaa toteuman ja toteuma-materiaalin, ja palauttaa lopuksi kaikki urakassa käytetyt materiaalit (yksi rivi per materiaali).
  Tiedon mukana tulee yhteenlaskettu summa materiaalin käytöstä.
  * Jos tähän funktioon tehdään muutoksia, pitäisi muutokset tehdä myös
  materiaalit/tallenna-toteumamateriaaleja! funktioon (todnäk)"
  [db user t toteumamateriaalit hoitokausi sopimus]
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus
                               (:urakka t))
  (log/debug "Tallenna toteuma: " (pr-str t) " ja toteumamateriaalit " (pr-str toteumamateriaalit))
  (jdbc/with-db-transaction [c db]
    ;; Jos toteumalla on positiivinen id, toteuma on olemassa
    (let [toteuma (if (and (:id t) (pos? (:id t)))
                    ;; Jos poistettu=true, halutaan toteuma poistaa.
                    ;; Molemmissa tapauksissa parametrina saatu toteuma tulee palauttaa
                    (if (:poistettu t)
                      (do
                        (log/debug "Poistetaan toteuma " (:id t))
                        (q/poista-toteuma! c (:id user) (:id t))
                        t)
                      (do
                        (log/debug "Pävitetään toteumaa " (:id t))
                        (q/paivita-toteuma! c (konv/sql-date (:alkanut t)) (konv/sql-date (:paattynyt t)) (:id user)
                                            (:suorittajan-nimi t) (:suorittajan-ytunnus t) (:lisatieto t) nil
                                            (:id t) (:urakka t))
                        t))
                    ;; Jos id:tä ei ole tai se on negatiivinen, halutaan luoda uusi toteuma
                    ;; Tässä tapauksessa palautetaan kyselyn luoma toteuma
                    (do
                      (log/debug "Luodaan uusi toteuma")
                      (q/luo-toteuma<!
                        c (:urakka t) (:sopimus t) (konv/sql-date (:alkanut t))
                        (konv/sql-date (:paattynyt t)) (:tyyppi t) (:id user)
                        (:suorittajan-nimi t)
                        (:suorittajan-ytunnus t)
                        (:lisatieto t)
                        nil
                        nil)))]
      (log/debug "Toteuman tallentamisen tulos:" (pr-str toteuma))

      (doall
        (for [tm toteumamateriaalit]
          ;; Positiivinen id = luodaan tai poistetaan toteuma-materiaali
          (if (and (:id tm) (pos? (:id tm)))
            (if (:poistettu tm)
              (do
                (log/debug "Poistetaan materiaalitoteuma " (:id tm))
                (materiaalit-q/poista-toteuma-materiaali! c (:id user) (:id tm)))
              (do
                (log/debug "Päivitä materiaalitoteuma "
                           (:id tm) " (" (:materiaalikoodi tm) ", " (:maara tm) ", " (:poistettu tm) "), toteumassa " (:id toteuma))
                (materiaalit-q/paivita-toteuma-materiaali!
                  c (:materiaalikoodi tm) (:maara tm) (:id user) (:id toteuma) (:id tm))))
            (do
              (log/debug "Luo uusi materiaalitoteuma (" (:materiaalikoodi tm) ", " (:maara tm) ") toteumalle " (:id toteuma))
              (materiaalit-q/luo-toteuma-materiaali<! c (:id toteuma) (:materiaalikoodi tm) (:maara tm) (:id user))))))
      ;; Jos saatiin parametrina hoitokausi, voidaan palauttaa urakassa käytetyt materiaalit
      ;; Tämä ei ole ehkä paras mahdollinen tapa hoitaa tätä, mutta toteuma/materiaalit näkymässä
      ;; tarvitaan tätä tietoa. -Teemu K
      (when hoitokausi
        (materiaalipalvelut/hae-urakassa-kaytetyt-materiaalit c user (:urakka toteuma) (first hoitokausi) (second hoitokausi) sopimus)))))

(defn poista-toteuma!
  [db user t]
  (roolit/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo} ;fixmepaivita roolit??
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
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus
                               (:urakka tiedot))
  (let [tehtavaid (:id tiedot)]
    (log/debug "Merkitään tehtava: " tehtavaid " maksuerä likaiseksi")
    (q/merkitse-toteumatehtavien-maksuerat-likaisiksi! db tehtavaid)

    (q/poista-tehtava! db (:id user) (:id tiedot))))

(defn hae-urakan-kokonaishintaisten-toteumien-tehtavat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm toimenpide tehtava]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [toteumat (into []
                       (comp
                         (filter #(not (nil? (:toimenpidekoodi %))))
                         (harja.geo/muunna-pg-tulokset :reittipiste_sijainti)
                         (map konv/alaviiva->rakenne))
                       (q/hae-urakan-kokonaishintaisten-toteumien-tehtavat db urakka-id
                                                                           sopimus-id
                                                                           (konv/sql-date alkupvm)
                                                                           (konv/sql-date loppupvm)
                                                                           toimenpide
                                                                           tehtava))
        kasitellyt-toteumarivit (konv/sarakkeet-vektoriin
                                  toteumat
                                  {:reittipiste :reittipisteet}
                                  :toteumaid)]
    kasitellyt-toteumarivit))

(defn hae-urakan-varustetoteumat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm tienumero]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (log/debug "Haetaan varustetoteumat: " urakka-id sopimus-id alkupvm loppupvm tienumero)
  (let [toteumat (into []
                       (comp
                         (map #(konv/string->keyword % :toimenpide))
                         (harja.geo/muunna-pg-tulokset :reittipiste_sijainti)
                         (map konv/alaviiva->rakenne))
                       (q/hae-urakan-varustetoteumat db
                                                     urakka-id
                                                     sopimus-id
                                                     (konv/sql-date alkupvm)
                                                     (konv/sql-date loppupvm)
                                                     (if tienumero true false)
                                                     tienumero))
        kasitellyt-toteumarivit (konv/sarakkeet-vektoriin
                                  toteumat
                                  {:reittipiste :reittipisteet}
                                  :id)]
    (log/debug "Palautetaan " (count kasitellyt-toteumarivit) " varustetoteuma(a)")
    kasitellyt-toteumarivit))

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
      (julkaise-palvelu http :urakan-toteumien-tehtavien-summat
                        (fn [user tiedot]
                          (hae-urakan-toteumien-tehtavien-summat db user tiedot)))
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
      (julkaise-palvelu http :urakan-muut-tyot
                        (fn [user tiedot]
                          (hae-urakan-muut-tyot db user tiedot)))
      (julkaise-palvelu http :tallenna-muiden-toiden-toteuma
                        (fn [user toteuma]
                          (tallenna-muiden-toiden-toteuma db user toteuma)))
      (julkaise-palvelu http :hae-toteumat-historiakuvaan
                        (fn [user tiedot]
                          (hae-toteumat-historiakuvaan db user tiedot)))
      (julkaise-palvelu http :tallenna-toteuma-ja-toteumamateriaalit
                        (fn [user tiedot]
                          (tallenna-toteuma-ja-toteumamateriaalit db user (:toteuma tiedot)
                                                                  (:toteumamateriaalit tiedot)
                                                                  (:hoitokausi tiedot)
                                                                  (:sopimus tiedot))))
      (julkaise-palvelu http :urakan-kokonaishintaisten-toteumien-tehtavat
                        (fn [user tiedot]
                          (hae-urakan-kokonaishintaisten-toteumien-tehtavat db user tiedot)))
      (julkaise-palvelu http :urakan-varustetoteumat
                        (fn [user tiedot]
                          (hae-urakan-varustetoteumat db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-toteumat
      :urakan-toteuma-paivat
      :hae-urakan-tehtavat
      :tallenna-urakan-toteuma
      :urakan-erilliskustannukset
      :urakan-muut-tyot
      :tallenna-muiden-toiden-toteuma
      :paivita-yk-hint-toteumien-tehtavat
      :tallenna-erilliskustannus
      :tallenna-toteuma-ja-toteumamateriaalit
      :poista-toteuma!
      :poista-tehtava!
      :hae-toteumat-historiakuvaan
      :urakan-varustetoteumat)
    this))
