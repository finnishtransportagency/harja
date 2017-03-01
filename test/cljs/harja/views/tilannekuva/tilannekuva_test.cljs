(ns harja.views.tilannekuva.tilannekuva-test
  (:require [harja.views.tilannekuva.tilannekuva :as tk]
            [clojure.test :refer-macros [deftest is]]))

(deftest nayta-vai-piilota?
  (is (true? (tk/nayta-vai-piilota? :nykytilanne)))
  (is (true? (tk/nayta-vai-piilota? :historiakuva)))
  (is (false? (tk/nayta-vai-piilota? :tienakyma)))
  (is (false? (tk/nayta-vai-piilota? :foobar))))
