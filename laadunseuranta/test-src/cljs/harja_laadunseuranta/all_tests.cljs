(ns ^:figwheel-always harja-laadunseuranta.all-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            [harja-laadunseuranta.projektiot-test]
            [harja-laadunseuranta.paikannus-test]
            [harja-laadunseuranta.ilmoitukset-test]
            [harja-laadunseuranta.kartta-test]
            [harja-laadunseuranta.reitintallennus-test]
            [harja-laadunseuranta.kaynnistyspainike-test]
            [harja-laadunseuranta.core-test]
            [harja-laadunseuranta.kamera-test]
            [harja-laadunseuranta.utils-test]
            [harja-laadunseuranta.ylapalkki-test]
            [harja-laadunseuranta.kitkamittaus-test]
            [harja-laadunseuranta.sovellus-test]
            [harja-laadunseuranta.mock.geolocation :as geolocation]

            ;; External javascriptit proj4 kirjastolle
            [proj4]
            [epsg3067]

            [cljs-react-test.utils]
            [cljs.test :as t :refer-macros [run-all-tests]]))

(geolocation/setup-mock-geolocation!)

(doo-tests 'harja-laadunseuranta.projektiot-test
           'harja-laadunseuranta.paikannus-test
           'harja-laadunseuranta.ilmoitukset-test
           'harja-laadunseuranta.kartta-test
           'harja-laadunseuranta.reitintallennus-test
           'harja-laadunseuranta.kaynnistyspainike-test
           'harja-laadunseuranta.core-test
           'harja-laadunseuranta.kamera-test
           'harja-laadunseuranta.utils-test
           'harja-laadunseuranta.ylapalkki-test
           'harja-laadunseuranta.kitkamittaus-test
           'harja-laadunseuranta.sovellus-test)
