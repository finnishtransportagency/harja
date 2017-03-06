(ns harja.domain.toimenpidekoodit)

(defn tuotteen-jarjestys [t2-koodi]
  (case t2-koodi
    "23100" 1 ; Talvihoito ensimmäisenä
    "23110" 2 ; Liikenneympäristön hoito toisena
    "23120" 3 ; Soratien hoito kolmantena
    ;; kaikki muut sen jälkeen
    4))
