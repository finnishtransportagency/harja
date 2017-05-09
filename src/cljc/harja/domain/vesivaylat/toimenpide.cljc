(ns harja.domain.vesivaylat.toimenpide)

(defn toimenpide-idlla [toimenpiteet id]
  (first (filter #(= (::id %) id) toimenpiteet)))

(defn toimenpiteet-tyolajilla [toimenpiteet tyolaji]
  (filter #(= (::tyolaji %) tyolaji) toimenpiteet))

(def tyolajit #{:kiinteat
                :poijut
                :viitat
                :vesiliikennemerkki})

(def tyolaji-fmt
  {:kiinteat "Kiinteät turvalaitteet"
   :poijut "Poijut"
   :viitat "Viitat"
   :vesiliikennemerkki "Vesiliikennemerkki"})