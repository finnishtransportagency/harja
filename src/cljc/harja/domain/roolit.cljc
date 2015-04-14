(ns harja.domain.roolit
  "Harjan käyttäjäroolit")

(def jarjestelmavastuuhenkilo          "jarjestelmavastuuhenkilo")
(def tilaajan-kayttaja                 "tilaajan kayttaja")
(def urakanvalvoja                     "urakanvalvoja")
;;(def vaylamuodon-vastuuhenkilo         "vaylamuodon vastuuhenkilo")
(def hallintayksikon-vastuuhenkilo     "hallintayksikon vastuuhenkilo")
(def liikennepaivystaja                "liikennepäivystäjä")
(def tilaajan-asiantuntija             "tilaajan asiantuntija")
(def tilaajan-laadunvalvontakonsultti  "tilaajan laadunvalvontakonsultti")
(def urakoitsijan-paakayttaja          "urakoitsijan paakayttaja")
(def urakoitsijan-urakan-vastuuhenkilo "urakoitsijan urakan vastuuhenkilo")
(def urakoitsijan-kayttaja             "urakoitsijan kayttaja")
(def urakoitsijan-laatuvastaava        "urakoitsijan laatuvastaava")

;; Tietokannan rooli enumin selvempi kuvaus
(def +rooli->kuvaus+
  {"jarjestelmavastuuhenkilo" "Järjestelmävastuuhenkilö"
   "tilaajan kayttaja" " Tilaajan käyttäjä"
   "urakanvalvoja" "Urakanvalvoja"
   ;;"vaylamuodon vastuuhenkilo" "Väylämuodon vastuuhenkilö"
   "hallintayksikon vastuuhenkilo" "Hallintayksikön vastuuhenkilö"
   "liikennepäivystäjä" "Liikennepäivystäjä"
   "tilaajan asiantuntija" "Tilaajan asiantuntija"
   "tilaajan laadunvalvontakonsultti" "Tilaajan laadunvalvontakonsultti"
   "urakoitsijan paakayttaja" "Urakoitsijan pääkäyttäjä"
   "urakoitsijan urakan vastuuhenkilo" "Urakoitsijan urakan vastuuhenkilö"
   "urakoitsijan kayttaja" "Urakoitsijan käyttäjä"
   "urakoitsijan laatuvastaava" "Urakoitsijan laatuvastaava"})

(defn rooli->kuvaus
  "Antaa roolin ihmisen luettavan kuvauksen käyttöliittymää varten."
  [rooli]
  (get +rooli->kuvaus+ rooli))
