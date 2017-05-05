(ns harja.domain.vesivaylat.toimenpide)

(defn toimenpide-idlla [toimenpiteet id]
  (first (filter #(= (::id %) id) toimenpiteet)))