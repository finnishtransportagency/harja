(ns harja.ui.taulukko.taulukko
  "Saman tyylinen kuin grid, mutta taulukolle annetaan näytettävä tila. Lisäksi ei määritellä miltä
   joku rivi näyttää vaan jätetään se käyttäjän vastuulle"
  (:require [harja.ui.taulukko.protokollat :as p]))

(defn taulukko
  [tila]
  {:pre [(sequential? tila)
         (every? #(satisfies? p/Jana %) tila)]}
  [:div.taulukko.taulukko-rivit
   (for [jana tila]
     (with-meta [p/piirra-jana jana]
                {:key (:janan-id jana)}))])