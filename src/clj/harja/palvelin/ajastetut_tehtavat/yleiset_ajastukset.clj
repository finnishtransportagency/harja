(ns harja.palvelin.ajastetut-tehtavat.yleiset-ajastukset
  "Kokoelma pienempiä yleisiä ajastuksia, jotka eivät sovi yhteen isommaksi tarkoitettuun palveluun"
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tapahtumat :as tapahtumat-kyselyt]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.kayttajat :as kayttajat-kyselyt]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]))

(defn- siivoa-tapahtuman-tiedot [db]
  (let [poistetut (first (tapahtumat-kyselyt/poista-viimeisimmat-tapahtumat db))]
    (log/info (format "tapahtuma-tiedot taulusta poistettiin %s riviä" (:maara poistetut)))))

(defn paivita-mahdolliset-suolatoteumat-kuluvalla-hoitokaudella
  "Päivitetään urakan suolatoteuma_reittipiste -taulun sisältö vain kuluneelta hoitovuodelta. Aiemmin tämä päivitti koko urakka-ajalta,
  mutta nyt kun suolatoteuma_reittipiste -taulun päivittäminen on hidastunut tietojen tarkentumisen myötä, niin
  potku ei enää riitä koko urakka-ajalta päivitellä."
  [db]
  (jdbc/with-db-transaction [db db]
    (let [urakat (suolarajoitus-kyselyt/hae-rajoitusaluetta-muokanneet-urakat db)]
      (doseq [{:keys [urakka_id] :as pr} urakat]
        (let [muokattu-nyt (c/to-sql-time (pvm/ajan-muokkaus (pvm/joda-timeksi (pvm/nyt)) false 1 :paiva))
              menossa-oleva-hoitokausi (pvm/paivamaaran-hoitokausi muokattu-nyt)]
          (suolarajoitus-kyselyt/paivita-suolatoteumat-urakalle db {:urakka_id urakka_id
                                                                    :alkupvm (pvm/iso8601 (first menossa-oleva-hoitokausi))
                                                                    :loppupvm (pvm/iso8601 (second menossa-oleva-hoitokausi))})))
      ;; Merkitse kaikki rajoitusalueet käsitellyiksi
      (suolarajoitus-kyselyt/nollaa-paivittyneet-rajoitusalueet! db))))

(defn- ajasta-siivoa-tapahtuman-tiedot [db]
  (log/info "Ajastetaan siivoa tapahtuman tiedot - ajetaan joka tunti.")
  (ajastettu-tehtava/ajasta-minuutin-valein 60 30
    (fn [_]
      (log/info "ajasta-minuutin-valein :: siivoa-tapahtuman-tiedot :: Alkaa " (pvm/nyt))
      ;; Aseta 30 sekunnin vanhenemisaika lukolle
      (lukot/yrita-ajaa-lukon-kanssa db "siivoa_tapahtuman_tiedot" #(siivoa-tapahtuman-tiedot db) 30)
      (log/info "ajasta-minuutin-valein :: siivoa-tapahtuman-tiedot :: Loppuu " (pvm/nyt)))))

(defn- ajasta-rajoitusalueen-suolatoteumat [db]
  (log/info "Ajastetaan siivoa tapahtuman tiedot - ajetaan kerran vuorokaudessa.")
  (ajastettu-tehtava/ajasta-paivittain [0 45 0]
    (fn [_]
      (lukot/yrita-ajaa-lukon-kanssa
        db
        "rajoitusalueen_suolatoteumat"
        #(do
           (log/info "ajasta-paivittain :: rajoitusalueen_suolatoteumat :: Alkaa " (pvm/nyt))
           (paivita-mahdolliset-suolatoteumat-kuluvalla-hoitokaudella db)
           (log/info "ajasta-paivittain :: rajoitusalueen_suolatoteumat :: Loppuu " (pvm/nyt)))))))

(defn tarkista-paattyneet-urakat
  "Tarkistaa tietokannasta urakat, jotka ovat päättyneet 90-180 päivää sitten,
  ja poistaa niiden käyttäjiltä lisäoikeudet urakkaan."
  [db]
  (let [paattyneet (urakat-kyselyt/hae-90pv-paattyneet-urakat db)]
    (if (seq paattyneet)
      (doseq [{:keys [id nimi loppupvm]} paattyneet]
        (log/info (format "Urakka '%s' on päättynyt %s. Poistetaan lisäoikeudet." nimi loppupvm))
        (kayttajat-kyselyt/jarjestelmakysely-poista-urakan-kayttajien-lisaoikeudet! db {:urakkaid id}))
      (log/info "Ei 90-180 päivää sitten päättyneitä urakoita."))))

(defn- ajasta-paattyneiden-urakoiden-tarkistus [db]
  (log/info "Ajastetaan päättyneiden urakoiden tarkistus - ajetaan kerran vuorokaudessa yöllä.")
  (ajastettu-tehtava/ajasta-paivittain [2 0 0]
    (fn [_]
      (lukot/yrita-ajaa-lukon-kanssa
        db
        "paattyneiden_urakoiden_tarkistus"
        #(do
           (log/info "ajasta-paivittain :: paattyneiden_urakoiden_tarkistus :: Alkaa " (pvm/nyt))
           (tarkista-paattyneet-urakat db)
           (log/info "ajasta-paivittain :: paattyneiden_urakoiden_tarkistus :: Loppuu " (pvm/nyt)))))))

(defrecord YleisetAjastuket []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :siivoa-tapahtuman-tiedot-ajastus (ajasta-siivoa-tapahtuman-tiedot db)
                :paivita-rajoitusalueen-suolatoteumat-ajastus (ajasta-rajoitusalueen-suolatoteumat db)
                :paattyneiden-urakoiden-tarkistus-ajastus (ajasta-paattyneiden-urakoiden-tarkistus db)))
  (stop [{poista-siivous :siivoa-tapahtuman-tiedot-ajastus
          poista-rajoitusalue :paivita-rajoitusalueen-suolatoteumat-ajastus
          poista-paattyneet :paattyneiden-urakoiden-tarkistus-ajastus :as this}]
    (do
      (poista-siivous)
      (poista-rajoitusalue)
      (poista-paattyneet))
    (dissoc this
      :siivoa-tapahtuman-tiedot-ajastus
      :paivita-rajoitusalueen-suolatoteumat-ajastus
      :paattyneiden-urakoiden-tarkistus-ajastus)))
