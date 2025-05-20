(ns harja.domain.muutos-domain
  (:require [harja.pvm :as pvm]
            ))

(def muutostyypit #{"pysyva"
                    "rahavaraus"
                    "johto-ja-hallintokorvaus"
                    "erillisrahoitettu"
                    "toteutuneet-maarat"
                    "maarapoikkeama"})
