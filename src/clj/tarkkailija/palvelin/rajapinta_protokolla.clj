(ns tarkkailija.palvelin.rajapinta-protokolla)

(defprotocol IRajapinta
  (lisaa [this nimi f])
  (poista [this nimi]))

(defn lisaa-palvelu [komponentti nimi f]
  {:pre [(keyword? nimi)
         (ifn? f)]}
  (lisaa komponentti nimi f))

(defn poista-palvelu [komponentti nimi]
  {:pre [(keyword? nimi)]}
  (poista komponentti nimi))
