(ns harja.ui.taulukko.taulukko
  "Saman tyylinen kuin grid, mutta taulukolle annetaan näytettävä tila. Lisäksi ei määritellä miltä
   joku rivi näyttää vaan jätetään se käyttäjän vastuulle"
  (:require [harja.ui.taulukko.protokollat :as p]))

(defn jana
  "Palauttaa jana(t) joiden id vastaa annettua"
  [taulukko id]
  (filter #(p/janan-id? % id) taulukko))

(defn janan-osa
  "Palauttaa janan elementi(t) joiden id vastaa annettua"
  [jana id]
  (filter #(p/osan-id? % id) (p/janan-osat jana)))

(defn taulukko
  [tila]
  {:pre [(sequential? tila)
         (every? #(satisfies? p/Jana %) tila)]}
  [:div.taulukko.taulukko-rivit
   (for [jana tila]
     (with-meta
       (p/piirra-jana jana)
       {:key (:janan-id jana)}))])