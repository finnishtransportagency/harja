(ns harja-laadunseuranta.tarkastusreittimuunnin.yhteiset
  "Tarkastusreittimuuntimen eri analyysien yhteiset koodit"
  (:require [taoensso.timbre :as log]))

(defn merkinnat-korjatulla-osalla
  "Korvaa indeksistä eteenpäin löytyvät merkinnät annetuilla uusilla merkinnöillä"
  [kaikki-merkinnat alku-indeksi uudet-merkinnat]
  (let [merkinnat-ennen-indeksia (take alku-indeksi kaikki-merkinnat)
        merkinnat-indeksin-jalkeen (drop (+ alku-indeksi (count uudet-merkinnat)) kaikki-merkinnat)]
    (vec (concat
           merkinnat-ennen-indeksia
           uudet-merkinnat
           merkinnat-indeksin-jalkeen))))

(defn laheisten-teiden-lahin-osuma-tielle
  "Etsii merkinnän läheisten teiden tiedoista lähimmän etäisyyden annetun merkinnän sijaintiin.
   Huomaa, että jos tiellä on useampi ajorata, tämä palauttaa lähimmän."
  [merkinta tie]
  (let [projisoitavaa-tieta-vastaavat-osoitteet (filter #(= (:tie %) tie) (:laheiset-tr-osoitteet merkinta))
        lahin-vastaava-osoite (first (sort-by :etaisyys-gps-pisteesta projisoitavaa-tieta-vastaavat-osoitteet))]
    lahin-vastaava-osoite))

(defn projisoi-merkinta-uudelle-tielle
  "Projisoi yksittäisen merkinnän annetulle tielle sillä oletuksella, että merkinnän läheisten teiden
   tiedoista löytyy kyseinen tie. Mikäli projisointi epäonnistuu (läheisistä teistä ei löydy annettua tietä),
   poistaa merkinnältä tieosoitteen (tarkastusreittimuunnin olettaa merkinnän olevan
   osa samaa tietä niin kauan kunnes oikea osoite löytyy. Merkintöjä ei sovi poistaa,
   sillä muuten saatetaan menettää havaintoja / mittauksia)."
  [merkinta tie]
  (if-let [lahin-vastaava-projisio (laheisten-teiden-lahin-osuma-tielle merkinta tie)]
    (do
      (log/debug "Projisoidaan merkintä tielle: " tie)
      (-> merkinta
          (assoc-in [:tr-osoite :tie] (:tie lahin-vastaava-projisio))
          (assoc-in [:tr-osoite :aosa] (:aosa lahin-vastaava-projisio))
          (assoc-in [:tr-osoite :aet] (:aet lahin-vastaava-projisio))
          (assoc-in [:tr-osoite :losa] (:losa lahin-vastaava-projisio))
          (assoc-in [:tr-osoite :let] (:let lahin-vastaava-projisio))))
    (do
      (log/debug (str "Ei voitu projisoida merkintää tielle: " tie ", ei ole riittävän lähellä"))
      (dissoc merkinta :tr-osoite))))

(defn projisoi-merkinnat-edelliselle-tielle
  "Projisoi annetut merkinnät takaisin merkintöjä edeltävälle tielle sillä oletuksella, että
   merkinnät ovat riittävän lähellä edellistä tietä (jos eivät ole, tieosoitteet nillataan)"
  [edeltava-merkinta merkinnat]
  (let [projisoitava-tie (get-in edeltava-merkinta [:tr-osoite :tie])
        korjatut-merkinnat (mapv #(projisoi-merkinta-uudelle-tielle % projisoitava-tie)
                                 merkinnat)]
    korjatut-merkinnat))