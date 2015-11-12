(ns harja.palvelin.integraatiot.api.validointi.toteumat
  (:require [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tarkasta-pvmvalin-validiteetti [alku loppu]
  (when (.after (pvm-string->java-sql-date alku) (pvm-string->java-sql-date loppu))
    (throw+ {:type    virheet/+viallinen-kutsu+
             :virheet [{:koodi  :toteuman-aika-viallinen
                        :viesti "Alkuaika on loppuajan jälkeen"}]})))

(defn tarkista-reittipisteet [toteuma]
  (let [toteuman-alku (pvm-string->java-sql-date (get-in toteuma [:reittitoteuma :toteuma :alkanut]))
        toteuman-loppu (pvm-string->java-sql-date (get-in toteuma [:reittitoteuma :toteuma :paattynyt]))
        reitti (get-in toteuma [:reittitoteuma :reitti])]
    (doseq [reittipiste reitti]
      (let [pisteen-aika (pvm-string->java-sql-date (get-in reittipiste [:reittipiste :aika]))]
        (when (or (.before pisteen-aika toteuman-alku) (.after pisteen-aika toteuman-loppu))
          (throw+ {:type    virheet/+viallinen-kutsu+
                   :virheet [{:koodi  :virheellinen-reittipiste
                              :viesti (format "Kaikkien reittipisteiden kirjausajat eivät ole toteuman alun: %s ja lopun: %s sisällä."
                                              toteuman-alku
                                              toteuman-loppu)}]}))))))