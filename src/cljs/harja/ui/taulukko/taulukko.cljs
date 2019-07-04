(ns harja.ui.taulukko.taulukko
  "Saman tyylinen kuin grid, mutta taulukolle annetaan näytettävä tila. Lisäksi ei määritellä miltä
   joku rivi näyttää vaan jätetään se käyttäjän vastuulle"
  (:require [harja.ui.taulukko.jana :as j]
            [harja.ui.taulukko.osa :as o]))

(defn jana
  "Palauttaa jana(t) joiden id vastaa annettua"
  [taulukko id]
  (filter #(j/janan-id? % id) taulukko))

(defn janan-osa
  "Palauttaa janan elementi(t) joiden id vastaa annettua"
  [jana id]
  (filter #(o/osan-id? % id) (j/janan-osat jana)))

(defn taulukko
  [tila]
  {:pre [(sequential? tila)
         (= 1 (count tila))
         (every? #(satisfies? j/Jana %) tila)]}
  [:div.taulukko.taulukko-rivit
   (for [jana tila]
     (with-meta
       (j/piirra-jana jana)
       {:key (:janan-id jana)}))])