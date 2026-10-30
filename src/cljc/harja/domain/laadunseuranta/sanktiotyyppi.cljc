(ns harja.domain.laadunseuranta.sanktiotyyppi)

(defn sanktiotyypin-nimi
  "Palauttaa sanktiotyypin nimen. Koodilla 0 sanktiotyyppi käyttää sanktiolajin nimeä."
  [sanktiolajin-nimi sanktiotyyppi]
  (if (= 0 (:koodi sanktiotyyppi))
    sanktiolajin-nimi
    (:nimi sanktiotyyppi)))

(def laskettavan-tyon-tekematta-jattamisen-koodit
  "MHU26:n työn tekemättä jättämisen sanktiotyypit, joilta ei kysytä perustelua."
  #{22 23})

(def laskettavan-sanktion-oletusperustelu "Laskettava sanktio")

(defn perusteluton-sanktio?
  "Tosi, kun sanktiolomake ei kysy perustelua: laskettava työn tekemättä jättäminen."
  [laji sanktiotyyppi]
  (and (= :tyon_tekematta_jattaminen laji)
       (contains? laskettavan-tyon-tekematta-jattamisen-koodit (:koodi sanktiotyyppi))))
