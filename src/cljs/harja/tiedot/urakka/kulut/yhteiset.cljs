(ns harja.tiedot.urakka.kulut.yhteiset
  (:require
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]))

(defn oikaisujen-summa [oikaisut]
  (or (apply + (map ::valikatselmus/summa (filter
                                            #(not (or (:poistettu %) (::muokkaustiedot/poistettu? %)))
                                            (vals oikaisut)))) 0))
