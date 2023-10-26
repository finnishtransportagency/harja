(ns harja.palvelin.integraatiot.api.validointi.toteumat
  (:require [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodi]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]))

(def toteuman-sallittu-aikavali
  [(pvm/luo-pvm-dec-kk 2014 1 1)
   (pvm/luo-pvm-dec-kk 2040 1 1)])

(defn validoi-toteuman-pvm-vali [alku loppu]
  (when (.after (aika-string->java-sql-date alku) (aika-string->java-sql-date loppu))
    (virheet/heita-viallinen-apikutsu-poikkeus {:koodi :toteuman-aika-viallinen
                                                :viesti "Totauman alkuaika on loppuajan jälkeen."})))

(defn validoi-ajan-vuosi [aika]
  (let [aika (pvm/rajapinta-str-aika->sql-timestamp aika)]
    (when (not (pvm/valissa? aika (first toteuman-sallittu-aikavali) (second toteuman-sallittu-aikavali)))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi :toteuman-aika-viallinen
         :viesti "Toteuman alku- tai loppuaika on viallinen, täytyy olla vuosien 2014-2040 sisällä."}))))

(defn tarkista-reittipisteet [reittitoteuma]
  (let [toteuman-alku (aika-string->java-sql-date (get-in reittitoteuma [:reittitoteuma :toteuma :alkanut]))
        toteuman-loppu (aika-string->java-sql-date (get-in reittitoteuma [:reittitoteuma :toteuma :paattynyt]))
        reitti (get-in reittitoteuma [:reittitoteuma :reitti])]
    (doseq [reittipiste reitti]
      (let [pisteen-aika (aika-string->java-sql-date (get-in reittipiste [:reittipiste :aika]))]
        (validoi-ajan-vuosi (get-in reittipiste [:reittipiste :aika]))
        (when (or (.before pisteen-aika toteuman-alku) (.after pisteen-aika toteuman-loppu))
          (virheet/heita-viallinen-apikutsu-poikkeus
            {:koodi :virheellinen-reittipiste
             :viesti (format "Kaikkien reittipisteiden kirjausajat eivät ole toteuman alun: %s ja lopun: %s sisällä."
                             toteuman-alku
                             toteuman-loppu)}))))))

(defn tarkista-tehtavat [db urakka-id tehtavat hinnoittelu]
  (doseq [tehtava tehtavat]
    (let [tehtava-apitunnus (get-in tehtava [:tehtava :id])
          vastaus (q-toimenpidekoodi/hae-hinnoittelu db {:urakka urakka-id :apitunnus tehtava-apitunnus} )
          _ (when (> (count vastaus) 1)
                (virheet/heita-viallinen-apikutsu-poikkeus
                  {:koodi :liikaa-osumia
                   :viesti (format "Apitunnuksella (id: %s) palautui liikaa osumia." tehtava-apitunnus)}))
          hinnoittelut (:hinnoittelu (konv/array->vec (first vastaus) :hinnoittelu))]

      (when (or (not hinnoittelut) (empty? hinnoittelut))
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi :virheellinen-tehtava
           :viesti (format "Tuntematon tehtävä (id: %s)." tehtava-apitunnus)}))

      (when (not-any? #(= % hinnoittelu) hinnoittelut)
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi :virheellinen-tehtava
           :viesti (format "Toteumalla on väärä hinnoittelu. Toteumalle annettiin hinnoittelu: %s, mutta toteumalle kirjatun tehtävän %s hinnoittelut ovat: %s. "
                           hinnoittelu tehtava-apitunnus hinnoittelut)})))))
