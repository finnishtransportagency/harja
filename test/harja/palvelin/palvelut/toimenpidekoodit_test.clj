(ns harja.palvelin.palvelut.toimenpidekoodit-test
  (:require [clojure.test :refer [use-fixtures]]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.testi :refer [testitietokanta-fixture testitietokanta]]))

(use-fixtures :once testitietokanta-fixture)


