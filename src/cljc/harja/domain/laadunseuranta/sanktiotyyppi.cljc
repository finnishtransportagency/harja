(ns harja.domain.laadunseuranta.sanktiotyyppi)

(defn sanktiotyypin-nimi
  "Palauttaa sanktiotyypin nimen. Koodilla 0 sanktiotyyppi käyttää sanktiolajin nimeä."
  [sanktiolajin-nimi sanktiotyyppi]
  (if (= 0 (:koodi sanktiotyyppi))
    sanktiolajin-nimi
    (:nimi sanktiotyyppi)))
