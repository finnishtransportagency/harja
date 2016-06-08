(ns harja.runner
  (:require  [doo.runner :refer-macros [doo-tests]]
             [harja.pvm-test]
             [harja.ui.dom-test]
             [harja.tiedot.urakka.suunnittelu-test]
             [harja.tiedot.urakka.yhatuonti-test]
             [harja.tiedot.muokkauslukko-test]
             [harja.views.urakka.siltatarkastukset-test]
             [harja.views.urakka.paallystysilmoitukset-test]
             [harja.views.urakka.paikkausilmoitukset-test]
             [harja.views.urakka.yllapitokohteet-test]
             ;; uusi testi tähän
             ))

(doo-tests 'harja.pvm-test
           'harja.ui.dom-test
           'harja.tiedot.urakka.suunnittelu-test
           'harja.tiedot.urakka.yhatuonti-test
           'harja.tiedot.muokkauslukko-test
           'harja.views.urakka.siltatarkastukset-test
           'harja.views.urakka.paallystysilmoitukset-test
           'harja.views.urakka.paikkausilmoitukset-test
           'harja.views.urakka.yllapitokohteet-test
           ;; uusi testi tähän
           )
