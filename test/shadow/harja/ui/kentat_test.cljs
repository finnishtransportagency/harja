(ns harja.ui.kentat-test
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.pvm :as pvm]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [react-testing-library-cljs.reagent.fire-event :as fire-event]
            [react-testing-library-cljs.reagent.render :as render]
            [react-testing-library-cljs.screen :as screen]))

(deftest numero-kentta-kasittelee-keskeneraiset-ja-muotoillut-arvot
  (let [data (r/atom nil)]
    (render/render! [kentat/tee-kentta {:desimaalien-maara 2
                                        :nimi :foo
                                        :tyyppi :numero}
                     data])
    (let [input (screen/get-by-role "textbox")]
      (is (= "" (.-value input)))

      (fire-event/change input {:target {:value "80"}})
      (is (= "80" (.-value input)))
      (is (= 80 @data))

      (fire-event/change input {:target {:value "100"}})
      (fire-event/change input {:target {:value "-"}})
      (is (= "-" (.-value input)))
      (is (= 100 @data))

      (fire-event/change input {:target {:value "-42"}})
      (is (= "-42" (.-value input)))
      (is (= -42 @data))

      (fire-event/change input {:target {:value "0."}})
      (is (= "0." (.-value input)))
      (is (zero? @data))

      (fire-event/change input {:target {:value "0.42"}})
      (is (= "0.42" (.-value input)))
      (is (= 0.42 @data))

      (fire-event/blur input)
      (is (= "0,42" (.-value input)))

      (reset! data 0.66)
      (r/flush)
      (is (= "0,66" (.-value input))))))

(deftest positiivinen-numero
  (let [data (r/atom nil)]
    (render/render! [kentat/tee-kentta {:nimi :foo
                                        :tyyppi :positiivinen-numero}
                     data])
    (let [input (screen/get-by-role "textbox")]
      (is (= "" (.-value input)))

      (fire-event/change input {:target {:value "80"}})
      (is (= "80" (.-value input)))
      (is (= 80 @data))

      (fire-event/change input {:target {:value "-12"}})
      (is (= "12" (.-value input)))
      (is (= 12 @data)))))

(deftest checkbox-ryhma
  (let [data (r/atom #{})
        vaihtoehdot ["Foo" "Bar" "Baz" "Quux"]]
    (render/render! [kentat/tee-kentta {:tyyppi :checkbox-group
                                        :vaihtoehdot vaihtoehdot
                                        :vaihtoehto-nayta str}
                     data])
    (let [checkboxit (screen/get-all-by-role "checkbox")]
      (is (= 4 (count checkboxit)))
      (is (every? #(false? (.-checked %)) checkboxit))

      (fire-event/click (first checkboxit))
      (is (true? (.-checked (first checkboxit))))
      (is (= #{"Foo"} @data))

      (r/flush)
      (fire-event/click (first (screen/get-all-by-role "checkbox")))
      (is (= #{} @data))

      (reset! data #{"Bar" "Quux"})
      (r/flush)
      (is (= 2 (count (filter #(true? (.-checked %))
                        (screen/get-all-by-role "checkbox"))))))))

(deftest checkbox-ryhma-muu
  (let [data (r/atom {"Foo" true
                      "Bar" false
                      "Muu" false
                      :muu "MUUTA"})]
    (render/render! [kentat/tee-kentta {:tyyppi :checkbox-group
                                        :vaihtoehdot ["Foo" "Bar" "Muu"]
                                        :vaihtoehto-nayta str
                                        :valittu-fn get
                                        :valitse-fn assoc
                                        :muu-vaihtoehto "Muu"
                                        :muu-kentta {:tyyppi :string
                                                     :nimi :muu}}
                     data])
    (let [checkboxit (screen/get-all-by-role "checkbox")]
      (is (= 1 (count (filter #(true? (.-checked %)) checkboxit))))
      (is (nil? (screen/query-by-role "textbox")))

      (fire-event/click (nth checkboxit 2))
      (is (= 2 (count (filter #(true? (.-checked %))
                        (screen/get-all-by-role "checkbox")))))
      (is (= "MUUTA" (.-value (screen/get-by-role "textbox"))))

      (fire-event/change (screen/get-by-role "textbox")
        {:target {:value "JOTAIN"}})
      (is (= {"Foo" true "Bar" false "Muu" true :muu "JOTAIN"} @data)))))

(deftest valinta
  (let [data (r/atom nil)
        vaihtoehdot ["abc" "kissa kävelee" "tikapuita pitkin taivaseen"]]
    (render/render! [kentat/tee-kentta {:nimi :foo
                                        :tyyppi :valinta
                                        :valinta-nayta #(if (nil? %) "Valitse" %)
                                        :valinnat vaihtoehdot}
                     data])
    (let [valintanappi (screen/get-by-role "button" {:name "Valitse"})]
      (is (some? valintanappi))
      (is (nil? (re-find #"open" (.-className (.-parentNode valintanappi)))))

      (fire-event/click valintanappi)
      (is (some? (re-find #"open" (.-className (.-parentNode valintanappi)))))

      (fire-event/click (screen/get-by-text "kissa kävelee"))
      (is (= "kissa kävelee" @data))
      (is (some? (screen/get-by-role "button" {:name "kissa kävelee"})))
      (is (nil? (re-find #"open" (.-className (.-parentNode valintanappi))))))))

(deftest pvm
  (let [data (r/atom nil)]
    (render/render! [kentat/tee-kentta {:tyyppi :pvm
                                        :placeholder "anna pvm"}
                     data])
    (let [input (screen/get-by-role "textbox")]
      (is (= "" (.-value input)))
      (is (= "anna pvm" (.getAttribute input "placeholder")))

      (fire-event/change input {:target {:value "66..."}})
      (is (= "" (.-value input)))
      (is (nil? @data))

      (fire-event/change input {:target {:value "12."}})
      (is (= "12." (.-value input)))
      (is (nil? @data))

      (fire-event/change input {:target {:value "7.7.2010"}})
      (is (= "7.7.2010" (.-value input)))
      (is (nil? @data))

      (fire-event/blur input)
      (is (pvm/sama-pvm? (pvm/->pvm "7.7.2010") @data))
      (is (nil? (screen/query-by-role "table")))

      (fire-event/click (.-parentNode (.-parentNode input)))
      (r/flush)
      (is (some? (screen/get-by-role "table")))
      (fire-event/click (js/document.getElementById "seuraava-kk"))
      (r/flush)
      (is (some? (screen/get-by-text "Elo")))

      (fire-event/click (js/document.getElementById "paiva_15"))
      (is (= "15.08.2010" (.-value input)))
      (is (pvm/sama-pvm? (pvm/->pvm "15.8.2010") @data)))))

(deftest pvm-aika
  (let [data (r/atom nil)
        odotettu (fn [paiva kuukausi vuosi tunti minuutti]
                   (pvm/aikana (pvm/luo-pvm vuosi (dec kuukausi) paiva)
                     tunti minuutti 0 0))]
    (render/render! [kentat/tee-kentta {:tyyppi :pvm-aika} data])
    (let [paiva (screen/get-by-role "textbox" {:name "päiväys"})
          kellonaika (screen/get-by-role "textbox" {:name "kellonaika"})]
      (is (nil? @data))
      (is (= "" (.-value paiva)))
      (is (= "" (.-value kellonaika)))
      (is (= "pp.kk.vvvv" (.getAttribute paiva "placeholder")))
      (is (= "tt:mm" (.getAttribute kellonaika "placeholder")))

      (fire-event/change paiva {:target {:value "8.4.1981"}})
      (fire-event/blur paiva)
      (is (nil? @data))

      (fire-event/change kellonaika {:target {:value "05:40"}})
      (fire-event/blur kellonaika)
      (is (= (odotettu 8 4 1981 5 40) @data))

      (reset! data (odotettu 29 8 1997 2 14))
      (r/flush)
      (is (= "29.08.1997" (.-value paiva)))
      (is (= "02:14" (.-value kellonaika)))

      (reset! data nil)
      (r/flush)
      (is (= "" (.-value paiva)))
      (is (= "" (.-value kellonaika)))

      (fire-event/change kellonaika {:target {:value "12:34"}})
      (fire-event/blur kellonaika)
      (fire-event/click (.-parentNode (.-parentNode paiva)))
      (r/flush)
      (fire-event/click (js/document.getElementById "tanaan"))
      (let [nyt (js/Date.)]
        (is (= (odotettu (.getDate nyt)
                 (inc (.getMonth nyt))
                 (.getFullYear nyt)
                 12
                 34)
               @data))))))
