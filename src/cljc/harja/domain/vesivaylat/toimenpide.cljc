(ns harja.domain.vesivaylat.toimenpide)

(defn toimenpide-idlla [toimenpiteet id]
  (first (filter #(= (::id %) id) toimenpiteet)))

(defn toimenpide-tyolajilla [toimenpiteet tyolaji]
  (first (filter #(= (::tyolaji %) tyolaji) toimenpiteet)))

(def tyolajit #{:kiinteat
                :poijut
                :viitat
                :vesiliikennemerkki})

(def tyolaji-fmt
  {:kiinteat "Kiinteät turvalaitteet"
   :poijut "Poijut"
   :viitat "Viitat"
   :vesiliikennemerkki "Vesiliikennemerkki"})