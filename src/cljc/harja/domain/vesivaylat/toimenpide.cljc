(ns harja.domain.vesivaylat.toimenpide)

(defn toimenpide-idlla [toimenpiteet id]
  (first (filter #(= (::id %) id) toimenpiteet)))

(defn toimenpide-turvalaitetyypilla [toimenpiteet turvalaitetyyppi]
  (first (filter #(= (::turvalaitetyyppi %) turvalaitetyyppi) toimenpiteet)))