(ns harja.ui.grid.gridin-muokkaus
  "Tämä namespace on lähinnä circular dependencyn estämiseksi ui komponenttien välillä.
   Eli tänne funktiot, joilla muutetaan gridin sisältämä data yhdestä muodosta toiseen ilman
   ohjaus kahvaa. Eli nämä muutokset eivät suoraan vaikuta gridin näyttämään sisältöön, sillä muokkaa!
   funktiota tai muuta vastaavaa ei kutsuta.")

(defn filteroi-uudet-poistetut
  "Ottaa datan muokkausgrid-muodossa (avaimet kokonaislukuja, jotka mappautuvat riveihin) ja palauttaa sellaiset
  rivit, jotka eivät ole uusia ja poistettuja. Paluuarvo on vectori mappeja."
  [rivit]
  (filter
    #(not (and (true? (:poistettu %))
               (neg? (:id %)))) (vals rivit)))

(defn poista-idt
  "Ottaa mapin ja polun. Olettaa, että polun päässä on vector.
  Palauttaa mapin, jossa polussa olevasta vectorista on jokaisesta itemistä poistettu id"
  [lomake polku]
  (assoc-in lomake polku (mapv
                           (fn [rivi] (dissoc rivi :id))
                           (get-in lomake polku))))

(defn poista-poistetut
  "Ottaa mapin ja polun. Olettaa, että polun päässä on vector.
   Palauttaa mapin, jossa polussa olevasta vectorista on jokaisesta itemistä poistettu elementit, joilla
   on :poistettu true."
  [lomake polku]
  (assoc-in lomake polku (filter (comp not :poistettu) (get-in lomake polku))))
