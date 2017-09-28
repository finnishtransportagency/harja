(ns harja.palvelin.palvelut.vesivaylat.viestinta
  (:require [harja.palvelin.palvelut.viestinta :as viestinta]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.materiaali :as m]))

(defn laheta-sposti-materiaalin-halyraja
  [fim email tiedot]
  (log/debug "Lähetetään sähköpostiviesti materiaalin hälyrajasta")
  (let [{vv-urakan-sampo-id :sampoid
         nimi :nimi
         halytysraja ::m/halytysraja
         maara-nyt ::m/maara-nyt} tiedot
        viestin-otsikko nimi
        viestin-vartalo (str "Hälytysraja: " halytysraja " määrä nyt: " maara-nyt)]
    (log/debug "TIEDOT: " tiedot)
    (viestinta/laheta-sposti-fim-kayttajarooleille
      {:fim                fim
       :email              email
       :urakka-sampoid     vv-urakan-sampo-id
       :fim-kayttajaroolit #{"ely urakanvalvoja"}
       :viesti-otsikko     viestin-otsikko
       :viesti-body        viestin-vartalo})))