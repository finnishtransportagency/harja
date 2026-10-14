(ns harja.ui.edistymispalkki-test
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.ui.openlayers.edistymispalkki :as palkki]))

(def nolla {:ladataan 0 :ladattu 0})
(def kesken {:ladataan 10 :ladattu 1})
(def valmis {:ladataan 10 :ladattu 10})
(def yli {:ladataan 10 :ladattu 15})

(deftest valmiiksi-toteaminen
  (is (false? (palkki/valmis? kesken)))
  (is (true? (palkki/valmis? valmis)))
  (is (true? (palkki/valmis? yli))))

(deftest nollaaminen
  (let [nollattu {:ladataan 0 :ladattu 0}
        nollattu? (fn [x] (= nollattu x))]
    (is (nollattu? (palkki/nollaa-jos-valmis valmis)))
    (is (= kesken (palkki/nollaa-jos-valmis kesken)))))

(deftest kasvattaminen
  (let [{v-ladataan :ladataan v-ladattu :ladattu} yli
        {:keys [ladataan ladattu]} (palkki/kasvata-ladataan-lukua yli)]
    (is (> ladataan v-ladataan))
    (is (= ladattu v-ladattu))
    (is (> ladataan ladattu))))

(deftest pakotettu-aloittaminen
  (let [nolla-atom (atom nolla)
        kesken-atom (atom kesken)]
    (palkki/pakota-aloitus! nolla-atom 1 10)
    (is (= @nolla-atom {:ladattu 1 :ladataan 10}))
    (palkki/pakota-aloitus! kesken-atom)
    (is (= @kesken-atom kesken))))
