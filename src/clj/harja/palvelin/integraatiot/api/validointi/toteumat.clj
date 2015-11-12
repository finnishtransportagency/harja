(ns harja.palvelin.integraatiot.api.validointi.toteumat
  (:require [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.urakat :as urakat]))

(defn tarkasta-pvmvalin-validiteetti [alku loppu]
  (when (.after (pvm-string->java-sql-date alku) (pvm-string->java-sql-date loppu))
    (virheet/heita-viallinen-apikutsu-poikkeus {:koodi  :toteuman-aika-viallinen
                                                :viesti "Totauman alkuaika on loppuajan jälkeen."})))

(defn tarkista-reittipisteet [toteuma]
  (let [toteuman-alku (pvm-string->java-sql-date (get-in toteuma [:reittitoteuma :toteuma :alkanut]))
        toteuman-loppu (pvm-string->java-sql-date (get-in toteuma [:reittitoteuma :toteuma :paattynyt]))
        reitti (get-in toteuma [:reittitoteuma :reitti])]
    (doseq [reittipiste reitti]
      (let [pisteen-aika (pvm-string->java-sql-date (get-in reittipiste [:reittipiste :aika]))]
        (when (or (.before pisteen-aika toteuman-alku) (.after pisteen-aika toteuman-loppu))
          (virheet/heita-viallinen-apikutsu-poikkeus
            {:koodi  :virheellinen-reittipiste
             :viesti (format "Kaikkien reittipisteiden kirjausajat eivät ole toteuman alun: %s ja lopun: %s sisällä."
                             toteuman-alku
                             toteuman-loppu)}))))))

(defn tarkista-tehtavat [db urakka-id tehtavat]
  (doseq [tehtava tehtavat]
    (let [tehtava-id (get-in tehtava [:tehtava :id])]
      (when (not (urakat/onko-urakalla-tehtavaa? db urakka-id tehtava-id))
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi  :virheellinen-tehtava
           :viesti (format "Urakalla id %s ei ole tehtävää %s." urakka-id tehtava-id)})))))