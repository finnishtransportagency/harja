(ns harja.domain.vesivaylat.toimenpide)

(defn toimenpide-idlla [toimenpiteet id]
  (first (filter #(= (::id %) id) toimenpiteet)))

(defn toimenpide-tyolajilla [toimenpiteet tyolaji]
  (first (filter #(= (::tyolaji %) tyolaji) toimenpiteet)))