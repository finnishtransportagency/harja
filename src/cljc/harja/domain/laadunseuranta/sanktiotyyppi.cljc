(ns harja.domain.laadunseuranta.sanktiotyyppi)

(defn sanktiotyypin-nimi
  "Palauttaa sanktiotyypin nimen. Koodilla 0 sanktiotyyppi käyttää sanktiolajin nimeä."
  [sanktiolajin-nimi sanktiotyyppi]
  (if (= 0 (:koodi sanktiotyyppi))
    sanktiolajin-nimi
    (:nimi sanktiotyyppi)))

(def laskettava-tyon-tekematta-jattaminen-koodi
  "MHU26:n laskettava työn tekemättä jättäminen (sohjo-oja ja lumivallin madallus)."
  22)

(def laskettavan-sanktion-oletusperustelu "Laskettava sanktio")

(defn perusteluton-sanktio?
  "Tosi, kun sanktiolomake ei kysy perustelua: laskettava työn tekemättä jättäminen."
  [laji sanktiotyyppi]
  (and (= :tyon_tekematta_jattaminen laji)
       (= laskettava-tyon-tekematta-jattaminen-koodi (:koodi sanktiotyyppi))))
