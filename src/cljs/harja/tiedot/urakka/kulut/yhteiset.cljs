(ns harja.tiedot.urakka.kulut.yhteiset
  (:require
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]))

(defn oikaisujen-summa [oikaisut hoitokauden-alkuvuosi]
  (or (apply + (map ::valikatselmus/summa (filter
                                            #(and (not (or (:poistettu %) (::muokkaustiedot/poistettu? %)))
                                                  (= (::valikatselmus/hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi))
                                            (vals oikaisut)))) 0))
