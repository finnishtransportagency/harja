(ns harja.tiedot.kanavat.yhteiset
  "Kanavat yhteiset
   Olisi tarkoitus siirtää hieman toistuvia konversiofunktioita, 
   esim kanavien_liikennetapahtumat.clj raportissa on paljon konversiofunktioita mitä on jo olemassa liikenne.cljs frontin koodissa")

#?(:cljs
   (defn onko-tarve-hakea-aikavali-muuttunut?
     "Palauttaa booleanin onko tarve tehdä uutta hakua jos käyttäjä muuttaa aikaväliä suodattimista"
     [valinnat uudet-valinnat tiedot-ladattu-konditio]
     (let [aikavali-uusi (:aikavali uudet-valinnat)
           vain-aikavali-muuttunut? (=
                                     (dissoc uudet-valinnat :aikavali)
                                     (dissoc valinnat :aikavali))
           ;; Onko aikaväli tällä hetkellä olemassa käyttäjän lomakkeessa?
           aikavali-olemassa? (boolean (and (first aikavali-uusi) (second aikavali-uusi)))
           ;; Tyhjennettiinkö aikaväli kokonaan?
           aikavali-tyhjennettiin? (and
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

#?(:cljs
   (defn lisaa-jarjestysnumero [data]
     (let [vanha-id (apply max (map :jarjestysnumero data))
           uusi-id (if (nil? vanha-id) 0 (inc vanha-id))]
       (conj (vec data) {:jarjestysnumero uusi-id}))))
