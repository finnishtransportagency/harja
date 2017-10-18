(ns harja.views.urakka.jarjestelma-asetukset-test
  (:require
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.pvm :refer [->pvm]]
    [harja.views.hallinta.jarjestelma-asetukset :as jarjestelmaasetukset]
    [harja.domain.geometriaaineistot :as geometria-aineistot]
    [harja.loki :refer [log]]
    [harja.tyokalut.functor :refer [fmap]]
    [cljs-time.core :as t]))


(deftest aineiston-voimassaolot-validit?
 (is (true? (jarjestelmaasetukset/aineiston-voimassaolot-epavalidit?
               [{::geometria-aineistot/nimi "testi"
                 ::geometria-aineistot/voimassaolo-alkaa (t/date-time 2017 1 1 0 0)
                 ::geometria-aineistot/voimassaolo-paattyy (t/date-time 2017 12 31 0 0)}
                {::geometria-aineistot/nimi "testi"
                 ::geometria-aineistot/voimassaolo-alkaa (t/date-time 2017 12 24 0 0)
                 ::geometria-aineistot/voimassaolo-paattyy (t/date-time 2018 1 1 0 0)}]))
      "Toisiaan voimassaololla leikkaavat materiaalit tunnistetaan")

  (is (false? (jarjestelmaasetukset/aineiston-voimassaolot-epavalidit?
               [{::geometria-aineistot/nimi "testi"
                 ::geometria-aineistot/voimassaolo-alkaa (t/date-time 2017 1 1 0 0)
                 ::geometria-aineistot/voimassaolo-paattyy (t/date-time 2017 12 31 0 0)}
                {::geometria-aineistot/nimi "testi"
                 ::geometria-aineistot/voimassaolo-alkaa (t/date-time 2018 1 1 0 0)
                 ::geometria-aineistot/voimassaolo-paattyy (t/date-time 2018 12 31 0 0)}]))
      "Validit aineistot tunnistetaan oikein")

 (is (false? (jarjestelmaasetukset/aineiston-voimassaolot-epavalidit?
               [{::geometria-aineistot/nimi "testi"
                 ::geometria-aineistot/voimassaolo-alkaa (t/date-time 2017 10 20 0 0)
                 ::geometria-aineistot/voimassaolo-paattyy (t/date-time 2017 10 22 0 0)}
                {::geometria-aineistot/nimi "testi"
                 ::geometria-aineistot/voimassaolo-alkaa (t/date-time 2017 10 22 1 0)
                 ::geometria-aineistot/voimassaolo-paattyy (t/date-time 2017 10 26 0)}]))
     "Ajat osataan huomioida oikein"))

