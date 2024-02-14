(ns harja.tiedot.kanavat.urakka.yhteiset
  "Kanavat yhteiset")

#?(:cljs
   (defn onko-tarve-hakea-aikavali-muuttunut? 
     "Palauttaa booleanin onko tarve tehdä uutta hakua jos käyttäjä muuttaa aikaväliä suodattimista"
     [valinnat uudet-valinnat tiedot-ladattu-konditio]
     (let [aikavali-vanha (:aikavali valinnat)
           aikavali-uusi (:aikavali uudet-valinnat)
           vain-aikavali-muuttunut? (=
                                     (dissoc uudet-valinnat :aikavali)
                                     (dissoc valinnat :aikavali))
           ;; Onko aikaväli tällä hetkellä olemassa käyttäjän lomakkeessa?
           aikavali-olemassa? (boolean (and (first aikavali-uusi) (second aikavali-uusi)))
           ;; Tyhjennettiinkö aikaväli kokonaan?
           aikavali-tyhjennettiin? (and
                                     (or
                                       (some? (first aikavali-vanha))
                                       (some? (second aikavali-vanha)))
                                     (nil? (first aikavali-uusi))
                                     (nil? (second aikavali-uusi)))
           ;; Onko tarve tehdä uutta hakua
           ;; Tässä katsotaan, muokkasiko, tai tyhjensikö käyttäjä aikaväli filtterin
           ;; Tämä tehty sen takia että ei tehdä turhia kutsuja, kun käyttäjä syöttää pelkästään alkupvm eikä loppupvm
           tarve-hakea? (or
                          tiedot-ladattu-konditio
                          aikavali-tyhjennettiin?
                          (not
                            (and vain-aikavali-muuttunut? (not aikavali-olemassa?))))]
       tarve-hakea?)))
