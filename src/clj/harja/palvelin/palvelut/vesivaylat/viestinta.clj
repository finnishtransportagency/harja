(ns harja.palvelin.palvelut.vesivaylat.viestinta
  (:require [harja.palvelin.palvelut.viestinta :as viestinta]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.materiaali :as m]))

(defn laheta-sposti-materiaalin-halyraja
  [fim email tiedot]
  (log/debug "Lähetetään sähköpostiviesti materiaalin hälyrajasta")
  (let [{vv-urakan-sampo-id :sampoid
         urakan-nimi :nimi
         materiaalin-nimi ::m/nimi
         halytysraja ::m/halytysraja
         maara-nyt ::m/maara-nyt} tiedot
        viestin-otsikko (format "Materiaali \"%s\" on vähissä!" materiaalin-nimi)
        viestin-vartalo (format "Urakassa %s on tullut materiaalin hälytysraja (%s) vastaan. Määrä on nyt: %s"
                                urakan-nimi halytysraja maara-nyt)
        kayttajaroolit #{"tilaajan urakanvalvoja"}]
    (viestinta/laheta-sposti-fim-kayttajarooleille
      {:fim                fim
       :email              email
       :urakka-sampoid     vv-urakan-sampo-id
       :fim-kayttajaroolit kayttajaroolit
       :viesti-otsikko     viestin-otsikko
       :viesti-body        viestin-vartalo})))
