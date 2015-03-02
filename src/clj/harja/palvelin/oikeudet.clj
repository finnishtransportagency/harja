(ns harja.palvelin.oikeudet
  "Kaikki palvelinpuolen käyttöoikeustarkistukset."
  )


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
     


  
