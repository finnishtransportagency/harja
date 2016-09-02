(ns harja.ui.localstorage-test
  (:require [harja.ui.localstorage :as localstorage]
            [cljs.test :as test :refer-macros [deftest is testing async]]
            [harja.testutils :refer [komponentti-fixture render change click
                                     paivita sel1 disabled?]]
            [reagent.core :as r]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(test/use-fixtures :each komponentti-fixture)

(deftest localstorage-toimii
  ;; Tekstin tallentaminen & luku
  (localstorage/tallenna-tekstiarvo "teksti" "harja")
  (is (= (localstorage/lue-tekstiarvo "teksti") "harja"))
  (is (nil? (localstorage/lue-tekstiarvo "tyhja")))

  ;; Totuusarvojen tallentaminen & luku
  (localstorage/tallenna-totuusarvo "totuus" true)
  (is (true? (localstorage/lue-totuusarvo "totuus")))
  (localstorage/tallenna-totuusarvo "totuus" false)
  (is (false? (localstorage/lue-totuusarvo "totuus")))
  (localstorage/tallenna-totuusarvo "totuus" nil)
  (is (false? (localstorage/lue-totuusarvo "totuus")))
  (is (nil? (localstorage/lue-totuusarvo "olematon"))))