(ns harja.palvelin.ajastetut-tehtavat.yleiset-ajastukset
  "Kokoelma pienempiä yleisiä ajastuksia, jotka eivät sovi yhteen isommaksi tarkoitettuun palveluun"
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.kyselyt.tapahtumat :as tapahtumat-kyselyt]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]))

(defn- siivoa-tapahtuman-tiedot [db]
  (let [poistetut (first (tapahtumat-kyselyt/poista-viimeisimmat-tapahtumat db))]
    (log/info (format "tapahtuma-tiedot taulusta poistettiin %s riviä" (:maara poistetut)))))

(defn paivita-mahdolliset-suolatoteumat
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
      (do
        (log/info "ajasta-minuutin-valein :: siivoa-tapahtuman-tiedot :: Alkaa " (pvm/nyt))
        ;; Aseta 30 sekunnin vanhenemisaika lukolle
        (lukot/yrita-ajaa-lukon-kanssa db "siivoa_tapahtuman_tiedot" #(siivoa-tapahtuman-tiedot db) 30)
        (log/info "ajasta-minuutin-valein :: siivoa-tapahtuman-tiedot :: Loppuu " (pvm/nyt))))))

(defn- ajasta-rajoitusalueen-suolatoteumat [db]
  (log/info "Ajastetaan siivoa tapahtuman tiedot - ajetaan kerran vuorokaudessa.")
  (ajastettu-tehtava/ajasta-paivittain [0 45 0]
    (fn [_]
      (lukot/yrita-ajaa-lukon-kanssa
        db
        "rajoitusalueen_suolatoteumat"
        #(do
           (log/info "ajasta-paivittain :: rajoitusalueen_suolatoteumat :: Alkaa " (pvm/nyt))
           (paivita-mahdolliset-suolatoteumat db)
           (log/info "ajasta-paivittain :: rajoitusalueen_suolatoteumat :: Loppuu " (pvm/nyt)))))))

(defrecord YleisetAjastuket []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :siivoa-tapahtuman-tiedot-ajastus (ajasta-siivoa-tapahtuman-tiedot db)
                :paivita-rajoitusalueen-suolatoteumat-ajastus (ajasta-rajoitusalueen-suolatoteumat db)))
  (stop [{poista-siivous :siivoa-tapahtuman-tiedot-ajastus
          poista-rajoitusalue :paivita-rajoitusalueen-suolatoteumat-ajastus :as this}]
    (do
      (poista-siivous)
      (poista-rajoitusalue))
    (dissoc this
      :siivoa-tapahtuman-tiedot-ajastus
      :paivita-rajoitusalueen-suolatoteumat-ajastus)))
