(ns harja.palvelin.integraatiot.api.validointi.toteumat
  (:require [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodi]
            [harja.kyselyt.konversio :as konv]))

(defn validoi-toteuman-pvm-vali [alku loppu]
  (when (.after (aika-string->java-sql-date alku) (aika-string->java-sql-date loppu))
    (virheet/heita-viallinen-apikutsu-poikkeus {:koodi :toteuman-aika-viallinen
                                                :viesti "Totauman alkuaika on loppuajan jälkeen."})))

(defn tarkista-reittipisteet [reittitoteuma]
  (let [toteuman-alku (aika-string->java-sql-date (get-in reittitoteuma [:reittitoteuma :toteuma :alkanut]))
        toteuman-loppu (aika-string->java-sql-date (get-in reittitoteuma [:reittitoteuma :toteuma :paattynyt]))
        reitti (get-in reittitoteuma [:reittitoteuma :reitti])]
    (doseq [reittipiste reitti]
      (let [pisteen-aika (aika-string->java-sql-date (get-in reittipiste [:reittipiste :aika]))]
        (when (or (.before pisteen-aika toteuman-alku) (.after pisteen-aika toteuman-loppu))
          (virheet/heita-viallinen-apikutsu-poikkeus
            {:koodi :virheellinen-reittipiste
             :viesti (format "Kaikkien reittipisteiden kirjausajat eivät ole toteuman alun: %s ja lopun: %s sisällä."
                             toteuman-alku
                             toteuman-loppu)}))))))

(defn tarkista-tehtavat [db tehtavat hinnoittelu]
  (doseq [tehtava tehtavat]
    (let [tehtava-apitunnus (get-in tehtava [:tehtava :id])
          hinnoittelut (:hinnoittelu (konv/array->vec (first (q-toimenpidekoodi/hae-hinnoittelu db tehtava-apitunnus)) :hinnoittelu))]

      (when (or (not hinnoittelut) (empty? hinnoittelut))
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi :virheellinen-tehtava
           :viesti (format "Tuntematon tehtävä (id: %s)." tehtava-apitunnus)}))

      (when (not-any? #(= % hinnoittelu) hinnoittelut)
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi :virheellinen-tehtava
           :viesti (format "Toteumalla on väärä hinnoittelu. Toteumalle annettiin hinnoittelu: %s, mutta toteumalle kirjatun tehtävän %s hinnoittelut ovat: %s. "
                           hinnoittelu tehtava-apitunnus hinnoittelut)})))))
