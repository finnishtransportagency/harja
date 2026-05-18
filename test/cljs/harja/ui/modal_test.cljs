(ns harja.ui.modal-test
  (:require [clojure.string :as str]
            [cljs.test :as t :refer-macros [deftest is]]
            [harja.testutils.shared-testutils :as u]
            [harja.ui.modal :as modal])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(deftest modaali-renderoi-harjan-omat-primitiiviluokat
  (komponenttitesti
    [modal/modal {:otsikko "Modaalin otsikko"
                  :nakyvissa? true
                  :footer [:button {:type "button"} "Sulje"]}
     [:div "Modaalin sisältö"]]

    (is (= 1 (count (u/sel [:.harja-modal]))))
    (is (= 1 (count (u/sel [:.harja-modal-tausta]))))
    (is (= 1 (count (u/sel [:.harja-modal-dialogi]))))
    (is (= 1 (count (u/sel [:.harja-modal-sisalto]))))
    (is (= 1 (count (u/sel [:.harja-modal-otsake]))))
    (is (= 1 (count (u/sel [:.harja-modal-otsakerivi]))))
    (is (= 1 (count (u/sel [:.harja-modal-runko]))))
    (is (= 1 (count (u/sel [:.harja-modal-alatunniste]))))
    (is (= 1 (count (u/sel [:.harja-modal-otsikko]))))
    (is (= 1 (count (u/sel [:.harja-modal-sulje]))))

    (is (= 0 (count (u/sel [:.modal-dialog]))))
    (is (= 0 (count (u/sel [:.modal-content]))))
    (is (= 0 (count (u/sel [:.modal-header]))))
    (is (= 0 (count (u/sel [:.modal-body]))))
    (is (= 0 (count (u/sel [:.modal-footer]))))))

(deftest modaali-renderoi-otsikon-kun-otsikolla-on-margin-bottom-muotoilu
  (komponenttitesti
    [modal/modal {:otsikko "Tyylitelty otsikko"
                  :otsikko-muotoilut {:font-size "28px" :margin-bottom "24px"}
                  :nakyvissa? true}
     [:div "Modaalin sisältö"]]

    (is (= "Tyylitelty otsikko" (u/text [:.harja-modal-otsikko])))
    (is (= 1 (count (u/sel [:.harja-modal-sulje]))))
    (is (str/includes? (or (.getAttribute (u/sel1 [:.harja-modal-otsakerivi]) "style") "")
                      "margin-bottom: 24px"))
    (is (not (str/includes? (or (.getAttribute (u/sel1 [:.harja-modal-otsikko]) "style") "")
                           "margin-bottom")))))