(ns harja.ui.tierekisteriosoite-test
  (:require [cljs.core.async :refer [<! chan put!]]
            [cljs.test :refer-macros [async deftest is]]
            [harja.tyokalut.vkm :as vkm]
            [harja.ui.kentat :as kentat]
            [react-testing-library-cljs.reagent.fire-event :as fire-event]
            [react-testing-library-cljs.reagent.render :as render]
            [react-testing-library-cljs.screen :as screen]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tie20-osa1-alkupiste
  {:type :point
   :coordinates [426938.1807000004 7212765.558800001]})

(def tie20-osa1-valipiste
  {:type :multiline
   :lines [{:type :line
            :points [[426938.1807000004 7212765.558800001]
                     [426961.68209999986 7212765.378899999]
                     [426978.40299999993 7212763.941300001]
                     [426991.6160000004 7212762.211199999]
                     [427003.70409999974 7212760.276799999]]}]})

(defn vastauskanava [kutsukanava vastaus]
  (fn [osoite]
    (put! kutsukanava osoite)
    (let [vastauskanava (chan 1)]
      (put! vastauskanava vastaus)
      vastauskanava)))

(deftest tierekisteriosoite
  (async done
    (go
      (let [data (r/atom nil)
            sijainti (r/atom nil)
            pistekutsu (chan 1)
            viivakutsu (chan 1)]
        (with-redefs [vkm/tieosoite->piste (vastauskanava pistekutsu tie20-osa1-alkupiste)
                      vkm/tieosoite->viiva (vastauskanava viivakutsu tie20-osa1-valipiste)]
          (render/render! [kentat/tee-kentta
                           {:tyyppi :tierekisteriosoite
                            :sijainti sijainti}
                           data])
          (let [[tie alkuosa alkuetaisyys loppuosa loppuetaisyys]
                (screen/get-all-by-role "textbox")]
            (is (every? empty? (map #(.-value %) [tie alkuosa alkuetaisyys loppuosa loppuetaisyys])))

            (fire-event/change tie {:target {:value "20"}})
            (r/flush)
            (is (= "20" (.-value tie)))

            (fire-event/change alkuosa {:target {:value "1"}})
            (fire-event/change alkuetaisyys {:target {:value "0"}})
            (r/flush)
            (let [sijainti-muuttui (chan 1)]
              (add-watch sijainti ::piste
                (fn [_ _ _ uusi]
                  (put! sijainti-muuttui uusi)))
              (fire-event/blur alkuetaisyys)
              (<! pistekutsu)
              (is (= tie20-osa1-alkupiste (<! sijainti-muuttui)))
              (remove-watch sijainti ::piste))
            (r/flush)

            (fire-event/change loppuosa {:target {:value "1"}})
            (fire-event/change loppuetaisyys {:target {:value "100"}})
            (r/flush)
            (let [sijainti-muuttui (chan 1)]
              (add-watch sijainti ::viiva
                (fn [_ _ _ uusi]
                  (put! sijainti-muuttui uusi)))
              (fire-event/blur loppuetaisyys)
              (<! viivakutsu)
              (is (= :multiline (:type (<! sijainti-muuttui))))
              (remove-watch sijainti ::viiva))
            (r/flush))
          (done))))))
