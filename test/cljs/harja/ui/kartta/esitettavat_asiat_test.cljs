(ns harja.ui.kartta.esitettavat-asiat-test
  (:require [cljs.test :as test :refer-macros [deftest is testing]]
            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]))

(deftest yllapitokohteen-esitys-kartalla
  (= (esitettavat-asiat/yllapitokohde
                :paallystys
                {:tila :paallystys-aloitettu
                 :nimi "Leppäjärven ramppi"}
                true
                "Päällystys")
     {:tila :paallystys-aloitettu,
      :nimi "Leppäjärven ramppi",
      :selite
      {:teksti "Päällystys, kesken",
       :vari ["rgb(0, 0, 0)" "rgb(255, 255, 0)" "rgb(77, 77, 77)"]},
      :alue nil}))