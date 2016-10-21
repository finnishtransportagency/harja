(ns ^:figwheel-always harja-laadunseuranta.all-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            [harja-laadunseuranta.tiedot.projektiot-test]
            [harja-laadunseuranta.tiedot.paikannus-test]
            [harja-laadunseuranta.ui.ilmoitukset-test]
            [harja-laadunseuranta.ui.kartta-test]
            [harja-laadunseuranta.tiedot.reitintallennus-test]
            [harja-laadunseuranta.ui.kaynnistyspainike-test]
            [harja-laadunseuranta.core-test]
            [harja-laadunseuranta.ui.kamera-test]
            [harja-laadunseuranta.utils-test]
            [harja-laadunseuranta.ylapalkki-test]
            [harja-laadunseuranta.ui.kitkamittaus-test]
            [harja-laadunseuranta.tiedot.sovellus-test]
            [harja-laadunseuranta.mock.geolocation :as geolocation]

            ;; External javascriptit proj4 kirjastolle
            [proj4]
            [epsg3067]

            [cljs-react-test.utils]
            [cljs.test :as t :refer-macros [run-all-tests]]))

(geolocation/setup-mock-geolocation!)

(doo-tests 'harja-laadunseuranta.tiedot.projektiot-test
           'harja-laadunseuranta.tiedot.paikannus-test
           'harja-laadunseuranta.ui.ilmoitukset-test
           'harja-laadunseuranta.ui.kartta-test
           'harja-laadunseuranta.tiedot.reitintallennus-test
           'harja-laadunseuranta.ui.kaynnistyspainike-test
           'harja-laadunseuranta.core-test
           'harja-laadunseuranta.ui.kamera-test
           'harja-laadunseuranta.utils-test
           'harja-laadunseuranta.ylapalkki-test
           'harja-laadunseuranta.ui.kitkamittaus-test
           'harja-laadunseuranta.tiedot.sovellus-test)
