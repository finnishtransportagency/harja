(ns harja.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [harja.tiedot.hallinta.harja-data-test]
            ))

(doo-tests 'harja.tiedot.hallinta.harja-data-test
           ;; uusi testi tähän
           )
