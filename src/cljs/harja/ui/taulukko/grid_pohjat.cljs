(ns harja.ui.taulukko.grid-pohjat
  (:require [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.alue :as alue]
            [harja.ui.taulukko.solu :as solu]
            [harja.ui.taulukko.grid-protokollat :as gp]))

(def grid-pohja-4 (grid/grid {:nimi ::root
                              :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
                              :koko (assoc-in konf/auto
                                              [:rivi :nimet]
                                              {::otsikko 0
                                               ::data 1
                                               ::yhteenveto 2})
                              :osat [(alue/rivi {:nimi ::otsikko
                                                 :koko (-> konf/livi-oletuskoko
                                                           (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                          3 "1fr"})
                                                           (assoc-in [:sarake :oletus-leveys] "2fr"))
                                                 :osat (mapv (fn [_]
                                                               (solu/tyhja))
                                                             (range 4))}
                                                [{:sarakkeet [0 4] :rivit [0 1]}])
                                     (alue/taulukko {:nimi ::data
                                                     :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                                     :koko konf/auto}
                                                    [(grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                                                                 :koko (assoc-in konf/auto
                                                                                 [:rivi :nimet]
                                                                                 {::data-yhteenveto 0
                                                                                  ::data-sisalto 1})
                                                                 :osat [(alue/rivi {:nimi ::data-yhteenveto
                                                                                    :koko {:seuraa {:seurattava ::otsikko
                                                                                                    :sarakkeet :sama
                                                                                                    :rivit :sama}}
                                                                                    :osat (mapv (fn [_]
                                                                                                  (solu/tyhja))
                                                                                                (range 4))}
                                                                                   [{:sarakkeet [0 4] :rivit [0 1]}])
                                                                        (alue/taulukko {:nimi ::data-sisalto
                                                                                        :alueet [{:sarakkeet [0 1] :rivit [0 12]}]
                                                                                        :koko konf/auto
                                                                                        :luokat #{"piillotettu"}}
                                                                                       (mapv (fn [_]
                                                                                               (alue/rivi {:koko {:seuraa {:seurattava ::otsikko
                                                                                                                           :sarakkeet :sama
                                                                                                                           :rivit :sama}}
                                                                                                           :osat (mapv (fn [_]
                                                                                                                         (solu/tyhja))
                                                                                                                       (range 4))}
                                                                                                          [{:sarakkeet [0 4] :rivit [0 1]}]))
                                                                                             (range 12)))]})])
                                     (alue/rivi {:nimi ::yhteenveto
                                                 :koko {:seuraa {:seurattava ::otsikko
                                                                 :sarakkeet :sama
                                                                 :rivit :sama}}
                                                 :osat (mapv (fn [_]
                                                               (solu/tyhja))
                                                             (range 4))}
                                                [{:sarakkeet [0 4] :rivit [0 1]}])]}))

(def grid-pohja-5 (let [grid-pohja (grid/grid-pohjasta grid-pohja-4)]
                    (gp/lisaa-sarake! grid-pohja (solu/tyhja))
                    (gp/aseta-koko! grid-pohja (-> konf/livi-oletuskoko
                                                   (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                  4 "1fr"})
                                                   (assoc-in [:sarake :oletus-leveys] "2fr")))
                    grid-pohja))

(def grid-pohja-7 (let [grid-pohja (grid/grid-pohjasta grid-pohja-5)]
                    (gp/lisaa-sarake! grid-pohja (solu/tyhja))
                    (gp/lisaa-sarake! grid-pohja (solu/tyhja))
                    (gp/aseta-koko! grid-pohja (-> konf/livi-oletuskoko
                                                   (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                  1 "1fr"})
                                                   (assoc-in [:sarake :oletus-leveys] "2fr")))
                    grid-pohja))