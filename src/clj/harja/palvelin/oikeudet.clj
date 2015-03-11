(ns harja.palvelin.oikeudet
  "Kaikki palvelinpuolen käyttöoikeustarkistukset."
  )


(def rooli:jarjestelmavastuuhenkilo          "jarjestelmavastuuhenkilo")
(def rooli:tilaajan-kayttaja                 "tilaajan kayttaja")
(def rooli:urakanvalvoja                     "urakanvalvoja")
(def rooli:vaylamuodon-vastuuhenkilo         "vaylamuodon vastuuhenkilo")
(def rooli:liikennepäivystaja                "liikennepäivystäjä")
(def rooli:tilaajan-asiantuntija             "tilaajan asiantuntija")
(def rooli:tilaajan-laadunvalvontakonsultti  "tilaajan laadunvalvontakonsultti")
(def rooli:urakoitsijan-paakayttaja          "urakoitsijan paakayttaja")
(def rooli:urakoitsijan-urakan-vastuuhenkilo "urakoitsijan urakan vastuuhenkilo")
(def rooli:urakoitsijan-kayttaja             "urakoitsijan kayttaja")
(def rooli:urakoitsijan-laatuvastaava        "urakoitsijan laatuvastaava")

(defn roolissa?
  "Tarkistaa onko käyttäjällä tietty rooli."
  [kayttaja rooli]
  (if ((:roolit kayttaja) rooli)
    true false))

(defn rooli-urakassa?
  "Tarkistaa onko käyttäjällä tietty rooli urakassa."
  [kayttaja rooli urakka-id]
   (if-let [urakkaroolit (some-> (:urakkaroolit kayttaja)
                                 (get urakka-id))]
     (if (urakkaroolit rooli)
       true
       false)
     false))
     


  
