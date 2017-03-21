(ns harja.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [harja.pvm-test]
            [harja.ui.dom-test]
            [harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot-test]
            [harja.tiedot.urakka.suunnittelu-test]
            [harja.tiedot.urakka.yhatuonti-test]
            [harja.tiedot.muokkauslukko-test]
            [harja.views.kartta.infopaneeli-test]
            [harja.views.urakka.siltatarkastukset-test]
            [harja.views.urakka.paallystysilmoitukset-test]
            [harja.views.urakka.paikkausilmoitukset-test]
            [harja.views.urakka.yllapitokohteet-test]
            [harja.views.urakka.valitavoitteet-test]
            [harja.views.urakka.yleiset-test]
            [harja.ui.historia-test]
            [harja.ui.kentat-test]
            [harja.ui.grid-test]
            [harja.ui.edistymispalkki-testi]
            [harja.fmt-test]
            [harja.tiedot.urakka.siirtymat-test]
            [harja.tiedot.tierekisteri.varusteet-test]
            [harja.ui.kartta.infopaneelin-sisalto-test]
            [harja.tiedot.tilannekuva.tienakyma-test]
            [harja.tiedot.urakka.aikataulu-test]
            [harja.views.kartta-test]
            [harja.tiedot.tilannekuva.tilannekuva-test]
            [harja.views.urakka.tiemerkinnan-yksikkohintaiset-tyot-test]
            [harja.views.kartta.tasot-test]
            ;; uusi testi tähän
            ))

(doo-tests 'harja.pvm-test
           'harja.ui.dom-test
           'harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot-test
           'harja.tiedot.urakka.suunnittelu-test
           'harja.tiedot.urakka.yhatuonti-test
           'harja.tiedot.muokkauslukko-test
           'harja.views.urakka.siltatarkastukset-test
           'harja.views.urakka.paallystysilmoitukset-test
           'harja.views.urakka.paikkausilmoitukset-test
           'harja.views.urakka.yllapitokohteet-test
           'harja.views.urakka.yleiset-test
           'harja.ui.historia-test
           'harja.ui.kentat-test
           'harja.ui.grid-test
           'harja.ui.edistymispalkki-testi
           'harja.views.kartta.infopaneeli-test
           'harja.views.urakka.valitavoitteet-test
           'harja.fmt-test
           'harja.tiedot.urakka.siirtymat-test
           'harja.tiedot.tierekisteri.varusteet-test
           'harja.ui.kartta.infopaneelin-sisalto-test
           'harja.tiedot.tilannekuva.tienakyma-test
           'harja.tiedot.urakka.aikataulu-test
           'harja.views.kartta-test
           'harja.tiedot.tilannekuva.tilannekuva-test
           'harja.views.urakka.tiemerkinnan-yksikkohintaiset-tyot-test
           'harja.views.kartta.tasot-test
           ;; uusi testi tähän
           )
