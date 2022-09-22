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
            [harja.pvm :as pvm]))

(defn- siivoa-tapahtuman-tiedot [db]
  (let [poistetut (first (tapahtumat-kyselyt/poista-viimeisimmat-tapahtumat db))]
    (log/info (format "tapahtuma-tiedot taulusta poistettiin %s riviä" (:maara poistetut)))))

(defn- paivita-mahdolliset-suolatoteumat
  "Päivitetään urakan suolatoteuma_reittipiste -taulun sisältö, jos rajoitusalueen tierekisteriosoitetta on muokattu.
   Ei ole merkitystä, että minkä vuoden rajoitusalue on muuttunut, koska rajoitusalueen tierekisteri koskee kaikkia vuosia
   Päivitetään siis urakan kaikki suolatoteuma_reittipisteet, mutta jaetaan se hoitokausiin, niin yksittäiset haut menevät nopeammin."
  [db]
  (jdbc/with-db-transaction [db db]
    (let [urakat (suolarajoitus-kyselyt/hae-rajoitusaluetta-muokanneet-urakat db)]
      (doseq [{:keys [urakka_id] :as pr} urakat]
        (let [urakan-hoitokaudet (urakka-kyselyt/hae-urakan-hoitokaudet db {:urakka_id urakka_id})]
          (doseq [{:keys [alkupvm loppupvm] :as kausi} urakan-hoitokaudet]
            (suolarajoitus-kyselyt/paivita-suolatoteumat-urakalle db {:urakka_id urakka_id
                                                                      :alkupvm alkupvm
                                                                      :loppupvm loppupvm}))))
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
    (do
      (fn [_]
        (lukot/yrita-ajaa-lukon-kanssa
          db
          "rajoitusalueen_suolatoteumat"
          #(do
             (log/info "ajasta-paivittain :: rajoitusalueen_suolatoteumat :: Alkaa " (pvm/nyt))
             (paivita-mahdolliset-suolatoteumat db)
             (log/info "ajasta-paivittain :: rajoitusalueen_suolatoteumat :: Loppuu " (pvm/nyt))))))))

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
