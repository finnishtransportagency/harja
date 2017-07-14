(ns harja.tyokalut.ui
  "Utility makroja UI-kehitykseen")

(defmacro for*
  "Kuten normaali for*, mutta tekee doall paluuarvolle ja automaattisesti
  asettaa numerojärjestyksen mukaisen keyn metadataan, jos sitä ei ole."
  [for-bindings body]
  (let [key-prefix (str (gensym "for*"))]
    `(doall
      (map-indexed
       (fn [i# item#]
         (if (contains? (meta item#) :key)
           item#
           (with-meta item#
             {:key (str ~key-prefix i#)})))
       (for ~for-bindings
         ~body)))))
